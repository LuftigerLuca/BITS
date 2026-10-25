package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.members.internal.GuildMemberService
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=$SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class OverviewTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var memberService: GuildMemberService

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        jdbc.execute(
            "TRUNCATE account_link, account_verification, overview_setting, audit_entry, system_log_entry, system_log_item, " +
                "event_publication",
        )
        memberService.forget()
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
    }

    @Test
    fun `members and verified members are unknown until the bot reports them`() {
        link("1", "w1")

        val overview = overview()

        assertEquals(null, overview.read<Int?>("$.members"))
        assertEquals(null, overview.read<Int?>("$.verifiedMembers"))
        assertEquals(null, overview.read<Any?>("$.country"))
    }

    @Test
    fun `verified members count only links of current members`() {
        link("1", "w1")
        reportMembers("1", "2", "3").andExpect { status { isNoContent() } }
        memberJoined("4")
        // Joined after the report, but before the next one; a link the backend has not caught up with yet.
        link("5", "w2")

        val overview = overview()

        assertEquals(4, overview.read<Int>("$.members"))
        assertEquals(1, overview.read<Int>("$.verifiedMembers"))
    }

    @Test
    fun `a full report deletes the links of everybody who is missing, without a role sync`() {
        link("1", "w1")
        link("2", "w2")
        verification("2")

        reportMembers("1", "3").andExpect { status { isNoContent() } }
        syncTrigger.awaitIdle()

        assertEquals(listOf("1"), linkedIds())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_verification", Int::class.java))
        assertEquals(listOf("ACCOUNT_REMOVED"), systemLogKinds())
        assertEquals("2", jdbc.queryForObject("SELECT discord_user_id FROM system_log_entry", String::class.java))
        assertEquals("Other", jdbc.queryForObject("SELECT warera_username FROM system_log_entry", String::class.java))
    }

    @Test
    fun `leaving deletes the link right away, and joining again does not bring it back`() {
        link("1", "w1")
        reportMembers("1", "2")

        memberLeft("1", "Luca").andExpect { status { isNoContent() } }
        assertEquals(emptyList(), linkedIds())
        assertEquals(1, overview().read<Int>("$.members"))
        assertEquals("Luca", jdbc.queryForObject("SELECT discord_name FROM system_log_entry", String::class.java))

        memberJoined("1")
        syncTrigger.awaitIdle()

        assertEquals(emptyList(), linkedIds())
        assertEquals(2, overview().read<Int>("$.members"))
        assertEquals(0, overview().read<Int>("$.verifiedMembers"))
        assertEquals(listOf("ACCOUNT_REMOVED"), systemLogKinds())
    }

    @Test
    fun `a report that would delete more than half of the links is refused and recorded once`() {
        (1..20).forEach { link("$it", "w$it") }
        reportMembers(*(1..20).map { "$it" }.toTypedArray())

        // 11 of 20 gone at once looks like a broken list.
        val partial = (12..20).map { "$it" }.toTypedArray()
        reportMembers(*partial).andExpect { status { isNoContent() } }
        reportMembers(*partial)

        assertEquals(20, linkedIds().size)
        assertEquals(20, overview().read<Int>("$.members"))
        assertEquals(listOf("MEMBER_REPORT_REFUSED"), systemLogKinds())

        // Ten leaving is no more than half, so it goes through.
        reportMembers(*(11..20).map { "$it" }.toTypedArray())
        assertEquals(10, linkedIds().size)
    }

    @Test
    fun `few links are deleted even if they are more than half`() {
        link("1", "w1")
        link("2", "w2")
        reportMembers("3")

        assertEquals(emptyList(), linkedIds())
    }

    @Test
    fun `the country shows its citizens once an admin picks it`() {
        val noCountry = overview()
        assertEquals(null, noCountry.read<Any?>("$.country"))

        mockMvc
            .put("/web/admin/overview/country") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"countryId":"de"}"""
            }.andExpect { status { isOk() } }
            .andExpect { jsonPath("$.name") { value("Germany") } }

        val overview = overview()
        assertEquals("de", overview.read("$.country.id"))
        assertEquals("Germany", overview.read("$.country.name"))
        assertEquals(true, overview.read("$.country.available"))
        assertEquals(982, overview.read<Int>("$.country.citizens"))
        assertEquals(925, overview.read<Int>("$.country.activeCitizens"))
        assertEquals(
            "Land der Startseite: Germany",
            jdbc.queryForObject("SELECT description FROM audit_entry WHERE action = 'OVERVIEW_COUNTRY_SET'", String::class.java),
        )

        setCountry("fr").andExpect { status { isOk() } }
        assertEquals("France", overview().read("$.country.name"))
        assertEquals(null, overview().read<Int?>("$.country.citizens"))
    }

    @Test
    fun `an unknown country is rejected`() {
        setCountry("xx").andExpect { status { isBadRequest() } }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM overview_setting", Int::class.java))
    }

    @Test
    fun `without WarEra the citizens are unavailable, but the Discord figures stay`() {
        setCountry("de")
        link("1", "w1")
        reportMembers("1", "2")
        warera.down = true

        val overview = overview().andExpect { status { isOk() } }

        assertEquals(2, overview.read<Int>("$.members"))
        assertEquals(1, overview.read<Int>("$.verifiedMembers"))
        assertEquals("Germany", overview.read("$.country.name"))
        assertEquals(false, overview.read("$.country.available"))
        assertEquals(null, overview.read<Int?>("$.country.citizens"))
        setCountry("fr").andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `users get 403 on the overview`() {
        val user = session(SessionUser.discord("7", "Member", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/overview") { cookie(user) }.andExpect { status { isForbidden() } }
        setCountry("de", user).andExpect { status { isForbidden() } }
    }

    @Test
    fun `member reports need the service token and valid IDs`() {
        mockMvc
            .put("/api/discord-members") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserIds":["1"]}"""
            }.andExpect { status { isUnauthorized() } }
        mockMvc
            .put("/api/discord-members") {
                header("Authorization", "Bearer $SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserIds":["not-an-id"]}"""
            }.andExpect { status { isBadRequest() } }
        assertEquals(null, overview().read<Int?>("$.members"))
    }

    private fun overview() = mockMvc.get("/web/admin/overview") { cookie(admin) }

    private fun setCountry(
        countryId: String,
        user: Cookie = admin,
    ) = mockMvc.put("/web/admin/overview/country") {
        cookie(user)
        contentType = MediaType.APPLICATION_JSON
        content = """{"countryId":"$countryId"}"""
    }

    private fun reportMembers(vararg discordUserIds: String) =
        mockMvc.put("/api/discord-members") {
            header("Authorization", "Bearer $SERVICE_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"discordUserIds":[${discordUserIds.joinToString(",") { "\"$it\"" }}]}"""
        }

    private fun memberJoined(discordUserId: String) = memberChange("joined", """{"discordUserId":"$discordUserId"}""")

    private fun memberLeft(
        discordUserId: String,
        name: String,
    ) = memberChange("left", """{"discordUserId":"$discordUserId","memberName":"$name"}""")

    private fun memberChange(
        path: String,
        body: String,
    ) = mockMvc.post("/api/discord-members/$path") {
        header("Authorization", "Bearer $SERVICE_TOKEN")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun link(
        discordUserId: String,
        wareraUserId: String,
    ) {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, now())",
            discordUserId,
            wareraUserId,
            warera.users[wareraUserId]?.username ?: wareraUserId,
        )
    }

    private fun verification(discordUserId: String) {
        jdbc.update(
            "INSERT INTO account_verification (discord_user_id, warera_user_id, warera_username, code, expires_at) " +
                "VALUES (?, 'w9', 'Someone', 'BITS-ABCDEF', now() + interval '10 minutes')",
            discordUserId,
        )
    }

    private fun linkedIds() = jdbc.queryForList("SELECT discord_user_id FROM account_link ORDER BY discord_user_id", String::class.java)

    private fun systemLogKinds() = jdbc.queryForList("SELECT kind FROM system_log_entry ORDER BY id", String::class.java)

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val SERVICE_TOKEN = "test-service-token-at-least-32-chars"
