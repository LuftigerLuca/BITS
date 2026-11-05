package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.externalapi.internal.ApiTokenRateLimit
import eu.luftiger.bits.backend.externalapi.internal.ApiTokenService
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The same configuration as most tests, so they share a Spring context; each context of its own holds connections to
// Postgres, of which the test container has a limited number.
@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class ExternalApiTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        clean()
        // 13:00 in Berlin.
        clock.now = Instant.parse("2026-03-10T12:00:00Z")
    }

    @AfterTest
    fun cleanUp() = clean()

    private fun clean() {
        jdbc.execute("TRUNCATE api_token, account_link, overview_snapshot, dsk, vote CASCADE")
        jdbc.update("DELETE FROM audit_entry WHERE action LIKE 'API_TOKEN%'")
        redis.keys("${ApiTokenRateLimit.KEY_PREFIX}*").takeIf { it.isNotEmpty() }?.let(redis::delete)
    }

    @Test
    fun `users get 403 on the tokens`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/api-tokens") { cookie(user) }.andExpect { status { isForbidden() } }
        create("""{"name":"Tool","expiresOn":"2026-04-01","scopes":["dsks:list"]}""", user).andExpect { status { isForbidden() } }
    }

    @Test
    fun `admins create a token, which is shown once and stored as hash only`() {
        val result =
            create("""{"name":" Statistik-Tool ","expiresOn":"2026-04-01","scopes":["dsks:list","accounts:list","dsks:list"]}""")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.token.name") { value("Statistik-Tool") }
                    jsonPath("$.token.scopes.length()") { value(2) }
                    jsonPath("$.token.status") { value("ACTIVE") }
                    jsonPath("$.token.lastUsedAt") { value(null) }
                    // Midnight after the last day, in Berlin.
                    jsonPath("$.token.expiresAt") { value(Instant.parse("2026-04-01T22:00:00Z").toEpochMilli()) }
                }.andReturn()
        val secret = JsonPath.read<String>(result.response.contentAsString, "$.secret")
        assertTrue(secret.startsWith("bits_"))

        assertEquals(ApiTokenService.hash(secret), jdbc.queryForObject("SELECT token_hash FROM api_token", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM api_token WHERE token_hash = ?", Int::class.java, secret))
        mockMvc.get("/web/admin/api-tokens") { cookie(admin) }.andExpect {
            status { isOk() }
            jsonPath("$.tokens[0].hint") { value(secret.take(9)) }
            jsonPath("$.tokens[0].secret") { doesNotExist() }
            jsonPath("$.scopes.length()") { value(7) }
            jsonPath("$.scopes[0].id") { value("accounts:list") }
        }
        assertEquals(
            listOf("API_TOKEN_CREATED" to "„Statistik-Tool“, gültig bis 01.04.2026, Scopes: dsks:list, accounts:list"),
            auditEntries(),
        )
    }

    @Test
    fun `a token needs a name, known scopes and an expiry within a year`() {
        create("""{"name":" ","expiresOn":"2026-04-01","scopes":["dsks:list"]}""").andExpect { status { isBadRequest() } }
        create("""{"name":"Tool","expiresOn":"2026-04-01","scopes":[]}""").andExpect { status { isBadRequest() } }
        create("""{"name":"Tool","expiresOn":"2026-04-01","scopes":["votes:write"]}""").andExpect { status { isBadRequest() } }
        create("""{"name":"Tool","expiresOn":"2026-03-10","scopes":["dsks:list"]}""").andExpect { status { isBadRequest() } }
        create("""{"name":"Tool","expiresOn":"2027-03-11","scopes":["dsks:list"]}""").andExpect { status { isBadRequest() } }
        create("""{"name":"Tool","expiresOn":"10.04.2026","scopes":["dsks:list"]}""").andExpect { status { isBadRequest() } }
        create("""{"name":"${"x".repeat(101)}","expiresOn":"2026-04-01","scopes":["dsks:list"]}""").andExpect { status { isBadRequest() } }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM api_token", Int::class.java))

        create("""{"name":"Tool","expiresOn":"2026-03-11","scopes":["dsks:list"]}""").andExpect { status { isCreated() } }
        create("""{"name":"Tool","expiresOn":"2027-03-10","scopes":["dsks:list"]}""").andExpect { status { isCreated() } }
    }

    @Test
    fun `requests without a valid token get 401`() {
        mockMvc.get("/external/v1/dsks").andExpect {
            status { isUnauthorized() }
            header { string(HttpHeaders.WWW_AUTHENTICATE, "Bearer") }
            jsonPath("$.status") { value(401) }
        }
        external("/external/v1/dsks", "bits_unknown").andExpect { status { isUnauthorized() } }
        external("/external/v1/dsks", "not-a-bits-token").andExpect { status { isUnauthorized() } }
        // Neither the session of the dashboard nor the service token of the bot opens the API.
        mockMvc.get("/external/v1/dsks") { cookie(admin) }.andExpect { status { isUnauthorized() } }
        external("/external/v1/dsks", "test-service-token-at-least-32-chars").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a token reaches exactly the endpoints of its scopes`() {
        val secret = token("dsks:list", "accounts:by-discord-id")

        external("/external/v1/dsks", secret).andExpect { status { isOk() } }
        external("/external/v1/accounts/discord/1", secret).andExpect { status { isNotFound() } }
        external("/external/v1/accounts", secret).andExpect {
            status { isForbidden() }
            jsonPath("$.detail") { value("Token lacks the scope accounts:list") }
        }
        external("/external/v1/accounts/warera/w1", secret).andExpect { status { isForbidden() } }
        external("/external/v1/votes", secret).andExpect { status { isForbidden() } }
    }

    @Test
    fun `the API only reads`() {
        val secret = token("dsks:list")

        mockMvc
            .post("/external/v1/dsks") {
                header(HttpHeaders.AUTHORIZATION, "Bearer $secret")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isForbidden() } }
        mockMvc.delete("/external/v1/dsks") { header(HttpHeaders.AUTHORIZATION, "Bearer $secret") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `expired and revoked tokens get 401`() {
        val secret = token("dsks:list", expiresOn = "2026-03-11")
        external("/external/v1/dsks", secret).andExpect { status { isOk() } }

        // Valid all of the last day, in Berlin.
        clock.now = Instant.parse("2026-03-11T22:59:59Z")
        external("/external/v1/dsks", secret).andExpect { status { isOk() } }
        clock.now = Instant.parse("2026-03-11T23:00:00Z")
        external("/external/v1/dsks", secret).andExpect { status { isUnauthorized() } }
        // A session of its own, since the one from the start of the test has expired by now.
        mockMvc
            .get("/web/admin/api-tokens") { cookie(session(SessionUser.admin("admin"))) }
            .andExpect { jsonPath("$.tokens[0].status") { value("EXPIRED") } }

        clock.now = Instant.parse("2026-03-10T12:00:00Z")
        val other = token("dsks:list")
        external("/external/v1/dsks", other).andExpect { status { isOk() } }
        val otherId = newestTokenId()
        revoke(otherId).andExpect { status { isNoContent() } }
        external("/external/v1/dsks", other).andExpect { status { isUnauthorized() } }
        revoke(otherId).andExpect { status { isNotFound() } }
        assertEquals("API_TOKEN_REVOKED" to "„Tool“", auditEntries().last())
    }

    @Test
    fun `tokens expiring within two weeks are marked`() {
        token("dsks:list", expiresOn = "2026-03-20")

        mockMvc.get("/web/admin/api-tokens") { cookie(admin) }.andExpect { jsonPath("$.tokens[0].status") { value("EXPIRING") } }
    }

    @Test
    fun `the last use is recorded at most once a minute`() {
        val secret = token("dsks:list")

        external("/external/v1/dsks", secret)
        assertEquals(clock.now, lastUsedAt())
        clock.advance(Duration.ofSeconds(30))
        external("/external/v1/dsks", secret)
        assertEquals(Instant.parse("2026-03-10T12:00:00Z"), lastUsedAt())
        clock.advance(Duration.ofSeconds(31))
        external("/external/v1/dsks", secret)
        assertEquals(clock.now, lastUsedAt())
    }

    @Test
    fun `each token is limited per minute`() {
        val secret = token("dsks:list")
        val other = token("dsks:list")

        repeat(60) { external("/external/v1/dsks", secret).andExpect { status { isOk() } } }
        external("/external/v1/dsks", secret).andExpect {
            status { isTooManyRequests() }
            header { string(HttpHeaders.RETRY_AFTER, "60") }
        }
        external("/external/v1/dsks", other).andExpect { status { isOk() } }
        clock.advance(Duration.ofMinutes(1))
        external("/external/v1/dsks", secret).andExpect { status { isOk() } }
    }

    @Test
    fun `accounts can be looked up and listed page by page`() {
        linkAccount("d1", "w1", "Alice", "2026-01-01T00:00:00Z")
        linkAccount("d2", "w2", "Bob", "2026-01-02T00:00:00Z")
        linkAccount("d3", "w3", "Carol", "2026-01-03T00:00:00Z")
        val secret = token("accounts:list", "accounts:by-discord-id", "accounts:by-warera-id")

        external("/external/v1/accounts/discord/d2", secret).andExpect {
            status { isOk() }
            jsonPath("$.wareraUserId") { value("w2") }
            jsonPath("$.wareraUsername") { value("Bob") }
            jsonPath("$.verifiedAt") { value("2026-01-02T00:00:00Z") }
        }
        external("/external/v1/accounts/warera/w3", secret).andExpect { jsonPath("$.discordUserId") { value("d3") } }
        external("/external/v1/accounts/warera/w9", secret).andExpect { status { isNotFound() } }
        external("/external/v1/accounts?page=1&size=2", secret).andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].discordUserId") { value("d3") }
            jsonPath("$.page") { value(1) }
            jsonPath("$.totalItems") { value(3) }
            jsonPath("$.totalPages") { value(2) }
        }
        external("/external/v1/accounts?size=101", secret).andExpect { status { isBadRequest() } }
        external("/external/v1/accounts?page=-1", secret).andExpect { status { isBadRequest() } }
        external("/external/v1/accounts/discord/${"1".repeat(33)}", secret).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `snapshots cover the last 30 days unless a range is given`() {
        jdbc.update(
            "INSERT INTO overview_snapshot (day, members, verified_members, country_id, citizens, active_citizens) " +
                "VALUES ('2026-03-10', 10, 5, 'de', 100, 40)",
        )
        jdbc.update("INSERT INTO overview_snapshot (day, members, verified_members) VALUES ('2026-02-01', 8, 4)")
        val secret = token("overview:snapshots")

        external("/external/v1/overview/snapshots", secret).andExpect {
            status { isOk() }
            jsonPath("$.from") { value("2026-02-09") }
            jsonPath("$.to") { value("2026-03-10") }
            jsonPath("$.days.length()") { value(1) }
            jsonPath("$.days[0].citizens") { value(100) }
        }
        external("/external/v1/overview/snapshots?from=2026-02-01&to=2026-02-28", secret).andExpect {
            jsonPath("$.days[0].day") { value("2026-02-01") }
            jsonPath("$.days[0].citizens") { value(null) }
        }
        external("/external/v1/overview/snapshots?from=2026-03-01&to=2026-02-01", secret).andExpect { status { isBadRequest() } }
        external("/external/v1/overview/snapshots?from=2025-01-01&to=2026-03-01", secret).andExpect { status { isBadRequest() } }
        external("/external/v1/overview/snapshots?from=gestern", secret).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `DSKs come with their military units`() {
        val dskId =
            jdbc.queryForObject("INSERT INTO dsk (name, role_id, created_at) VALUES ('DSK1', 'r1', now()) RETURNING id", Long::class.java)
        jdbc.update(
            "INSERT INTO dsk_military_unit (mu_id, dsk_id, mu_name, assigned_at) VALUES ('m1', ?, 'Luftwaffe', '2026-01-01T00:00:00Z')",
            dskId,
        )

        external("/external/v1/dsks", token("dsks:list")).andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("DSK1") }
            jsonPath("$[0].roleId") { value("r1") }
            jsonPath("$[0].militaryUnits[0].id") { value("m1") }
            jsonPath("$[0].militaryUnits[0].assignedAt") { value("2026-01-01T00:00:00Z") }
        }
    }

    @Test
    fun `votes show their result only once evaluated`() {
        val open = insertVote("Offen?", "OPEN", "2026-03-09T00:00:00Z")
        val evaluated = insertVote("Ausgewertet?", "EVALUATED", "2026-03-01T00:00:00Z")
        jdbc.update(
            "INSERT INTO vote_party_result (vote_id, position, party_name, party_id, yes_count, no_count, abstain_count, eligible_count) " +
                "VALUES (?, 0, 'Partei', 'p1', 3, 1, 0, 6), (?, 1, NULL, NULL, 1, 0, 1, 4)",
            evaluated,
            evaluated,
        )
        // A ballot of the open vote must not show up anywhere.
        jdbc.update(
            "INSERT INTO vote_ballot (vote_id, discord_user_id, warera_user_id, choice, cast_at) VALUES (?, 'd1', 'w1', 'YES', now())",
            open,
        )
        val secret = token("votes:list", "votes:get")

        external("/external/v1/votes", secret).andExpect {
            status { isOk() }
            jsonPath("$.items[0].id") { value(open) }
            jsonPath("$.items[0].state") { value("OPEN") }
            jsonPath("$.items[0].result") { value(null) }
            jsonPath("$.items[1].id") { value(evaluated) }
        }
        external("/external/v1/votes/$evaluated", secret).andExpect {
            status { isOk() }
            jsonPath("$.result.yes") { value(4) }
            jsonPath("$.result.no") { value(1) }
            jsonPath("$.result.abstain") { value(1) }
            jsonPath("$.result.voted") { value(6) }
            jsonPath("$.result.eligible") { value(10) }
            jsonPath("$.result.parties[0].partyName") { value("Partei") }
            jsonPath("$.result.parties[1].partyName") { value(null) }
            jsonPath("$.startedBy") { doesNotExist() }
            jsonPath("$.nonVoters") { doesNotExist() }
        }
        external("/external/v1/votes/$open", secret).andExpect { jsonPath("$.result") { value(null) } }
        external("/external/v1/votes/999999", secret).andExpect { status { isNotFound() } }
    }

    @Test
    fun `the documentation is public and covers only the third-party API`() {
        val document =
            mockMvc
                .get("/external/docs/openapi")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val paths = JsonPath.read<Map<String, Any>>(document, "$.paths").keys
        assertTrue(paths.isNotEmpty())
        assertTrue(paths.all { it.startsWith("/external/v1/") }, "Paths outside of the API: $paths")
        assertEquals(
            listOf("accounts:list"),
            JsonPath.read<List<String>>(document, "$.paths['/external/v1/accounts'].get.security[0].apiToken"),
        )
        assertNotNull(JsonPath.read<Any>(document, "$.paths['/external/v1/accounts'].get.responses['429']"))

        mockMvc.get("/external/docs").andExpect { status { is3xxRedirection() } }
        mockMvc.get("/external/swagger-ui/index.html").andExpect { status { isOk() } }
        mockMvc.get("/external/docs/openapi/swagger-config").andExpect { status { isOk() } }
    }

    @Test
    fun `the old default paths of springdoc stay closed`() {
        mockMvc.get("/v3/api-docs").andExpect { status { isForbidden() } }
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isForbidden() } }
    }

    private fun token(
        vararg scopes: String,
        expiresOn: String = "2026-06-01",
    ): String {
        val body = """{"name":"Tool","expiresOn":"$expiresOn","scopes":[${scopes.joinToString { "\"$it\"" }}]}"""
        val result = create(body).andExpect { status { isCreated() } }.andReturn()
        return JsonPath.read(result.response.contentAsString, "$.secret")
    }

    private fun create(
        body: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/api-tokens") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun revoke(id: Long) = mockMvc.delete("/web/admin/api-tokens/$id") { cookie(admin) }

    private fun newestTokenId() = requireNotNull(jdbc.queryForObject("SELECT max(id) FROM api_token", Long::class.java))

    private fun external(
        path: String,
        secret: String,
    ): ResultActionsDsl = mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, "Bearer $secret") }

    private fun lastUsedAt(): Instant? = jdbc.queryForObject("SELECT last_used_at FROM api_token", Timestamp::class.java)?.toInstant()

    private fun linkAccount(
        discordUserId: String,
        wareraUserId: String,
        username: String,
        verifiedAt: String,
    ) = jdbc.update(
        "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, ?::timestamptz)",
        discordUserId,
        wareraUserId,
        username,
        verifiedAt,
    )

    private fun insertVote(
        question: String,
        state: String,
        startedAt: String,
    ): Long =
        requireNotNull(
            jdbc.queryForObject(
                "INSERT INTO vote (question, role_id, role_name, channel_id, state, started_at, started_by_name) " +
                    "VALUES (?, 'r1', 'Bürger', 'c1', ?, ?::timestamptz, 'Admin') RETURNING id",
                Long::class.java,
                question,
                state,
                startedAt,
            ),
        )

    private fun auditEntries() =
        jdbc.query("SELECT action, description FROM audit_entry WHERE action LIKE 'API_TOKEN%' ORDER BY id") { rs, _ ->
            rs.getString("action") to rs.getString("description")
        }

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}
