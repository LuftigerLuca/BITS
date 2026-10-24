package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncService
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.VerificationCheckedEvent
import eu.luftiger.bits.shared.decodeEvent
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraBadRequestException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCompany
import eu.luftiger.bits.warera.WareraCountry
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraMilitaryUnit
import eu.luftiger.bits.warera.WareraNotFoundException
import eu.luftiger.bits.warera.WareraSchemaException
import eu.luftiger.bits.warera.WareraUser
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class RoleSyncTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncService: RoleSyncService

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var warera: FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        jdbc.execute("TRUNCATE account_link, account_verification, role_rule, discord_role, event_publication")
        redisTemplate.delete(EventStream.KEY)
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        reportRoles(
            """{"id":"r-citizen","name":"Bürger Deutschland","color":16711680,"position":3,"assignable":true}""",
            """{"id":"r-minister","name":"Minister","color":null,"position":2,"assignable":true}""",
            """{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""",
            """{"id":"r-mod","name":"Moderator","color":null,"position":9,"assignable":false}""",
        )
    }

    @Test
    fun `users get 403 on the rules`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/role-sync/rules") { cookie(user) }.andExpect { status { isForbidden() } }
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""", user).andExpect { status { isForbidden() } }
    }

    @Test
    fun `only assignable roles can be picked`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")

        val roles = mockMvc.get("/web/admin/role-sync/roles") { cookie(admin) }
        assertEquals(listOf("Bürger Deutschland", "Minister", "Veteran"), roles.read("$[*].name"))
        assertEquals(listOf(false, false, true), roles.read("$[*].managed"))
        createRule("""{"roleId":"r-mod","type":"LEVEL","threshold":10}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `admin creates a rule of each type and deletes it`() {
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""").andExpect {
            status { isCreated() }
            jsonPath("$.label") { value("Staatsbürgerschaft Germany") }
            jsonPath("$.status") { value("ACTIVE") }
        }
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"MINISTER_OF_DEFENSE"}""")
            .andExpect { jsonPath("$.label") { value("Verteidigungsminister in Germany") } }
        createRule("""{"roleId":"r-veteran","type":"MILITARY_UNIT","muId":"m1"}""")
            .andExpect { jsonPath("$.label") { value("Mitglied der MU Deutsche Marine") } }
        val id =
            ruleId(createRule("""{"roleId":"r-veteran","type":"MILITARY_RANK","threshold":40}"""))

        assertEquals(
            listOf(
                "Staatsbürgerschaft Germany",
                "Verteidigungsminister in Germany",
                "Mitglied der MU Deutsche Marine",
                "Militärrang ab 40",
            ),
            mockMvc.get("/web/admin/role-sync/rules") { cookie(admin) }.read("$[*].label"),
        )
        mockMvc.delete("/web/admin/role-sync/rules/$id") { cookie(admin) }.andExpect { status { isNoContent() } }
        mockMvc.delete("/web/admin/role-sync/rules/$id") { cookie(admin) }.andExpect { status { isNotFound() } }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM role_rule", Int::class.java))
    }

    @Test
    fun `incomplete or unknown conditions are rejected`() {
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"xx"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-veteran","type":"MILITARY_UNIT","muId":"m9"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-veteran","type":"MILITARY_UNIT","muId":"not an id"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":0}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-gone","type":"LEVEL","threshold":5}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `rule change syncs all members`() {
        link("1", "w1")
        link("2", "w2")

        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")

        assertEquals(
            RoleSyncEvent(
                managedRoleIds = listOf("r-citizen"),
                discordUserIds = null,
                members = mapOf("1" to MemberRoles(listOf(RoleGrant("r-citizen", "BITS: Staatsbürgerschaft Germany")))),
                occurredAt = clock.millis(),
            ),
            awaitEvents(1).single(),
        )
    }

    @Test
    fun `rule change reuses the WarEra data of the last sync, the hourly sync loads it again`() {
        link("1", "w1")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitEvents(1)

        warera.users["w1"] = warera.users.getValue("w1").copy(leveling = WareraUser.Leveling(5))
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        assertEquals(setOf("r-citizen", "r-veteran"), grantedRoles(awaitEvents(2).last(), "1"))

        syncService.syncAll(refresh = true)
        assertEquals(setOf("r-citizen"), grantedRoles(awaitEvents(3).last(), "1"))
    }

    @Test
    fun `linking syncs the member and takes the roles of the previous account`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        awaitEvents(1)

        verify("2", "Luftiger")

        val event = awaitEvents(2).last() as RoleSyncEvent
        assertEquals(listOf("2", "1"), event.discordUserIds)
        assertEquals(setOf("2"), event.members.keys)
    }

    @Test
    fun `office rules check the government of the country`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"PRESIDENT"}""")

        assertEquals(setOf("2"), (awaitEvents(1).single() as RoleSyncEvent).members.keys)
    }

    @Test
    fun `failed lookups keep the roles of the affected members`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"PRESIDENT"}""")
        awaitEvents(2)

        warera.failingUsers += "w1"
        warera.governmentDown = true
        syncService.syncAll(refresh = true)

        val event = awaitEvents(3).last() as RoleSyncEvent
        assertEquals(MemberRoles(grants = emptyList(), keepRoleIds = listOf("r-citizen", "r-minister")), event.members["1"])
        assertEquals(MemberRoles(grants = emptyList(), keepRoleIds = listOf("r-minister")), event.members["2"])
    }

    @Test
    fun `rules of a deleted role are orphaned and not run`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitEvents(2)

        reportRoles("""{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""")

        val rules = mockMvc.get("/web/admin/role-sync/rules") { cookie(admin) }
        assertEquals(listOf("ACTIVE", "ORPHANED"), rules.read("$[*].status"))
        assertEquals(null, rules.read<String?>("$[1].roleName"))
        syncService.syncAll(refresh = false)
        assertEquals(listOf("r-veteran"), (awaitEvents(3).last() as RoleSyncEvent).managedRoleIds)
    }

    @Test
    fun `countries and military units can be picked`() {
        assertEquals(listOf("France", "Germany"), mockMvc.get("/web/admin/role-sync/countries") { cookie(admin) }.read("$[*].name"))
        mockMvc
            .get("/web/admin/role-sync/military-units") {
                cookie(admin)
                param("query", "Marine")
            }.andExpect { jsonPath("$[0].name") { value("Deutsche Marine") } }

        warera.down = true
        mockMvc.get("/web/admin/role-sync/countries") { cookie(admin) }.andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `role report needs the service token`() {
        mockMvc
            .put("/api/discord-roles") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roles":[]}"""
            }.andExpect { status { isUnauthorized() } }
    }

    private fun reportRoles(vararg roles: String) {
        mockMvc
            .put("/api/discord-roles") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roles":[${roles.joinToString(",")}]}"""
            }.andExpect { status { isNoContent() } }
    }

    private fun createRule(
        body: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/role-sync/rules") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun ruleId(result: ResultActionsDsl): Long = result.read<Number>("$.id").toLong()

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun link(
        discordUserId: String,
        wareraUserId: String,
    ) {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, now())",
            discordUserId,
            wareraUserId,
            warera.users.getValue(wareraUserId).username,
        )
    }

    /** Links through the API, which is what starts the sync of the member. */
    private fun verify(
        discordUserId: String,
        username: String,
    ) {
        val started =
            mockMvc
                .post("/api/verifications") {
                    header("Authorization", "Bearer test-service-token-at-least-32-chars")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"discordUserId":"$discordUserId","wareraUsername":"$username"}"""
                }.andReturn()
                .response.contentAsString
        val code = JsonPath.read<String>(started, "$.code")
        warera.companyName = code
        mockMvc
            .post("/api/verifications/check") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"$discordUserId","code":"$code"}"""
            }.andExpect { jsonPath("$.type") { value("checking") } }
        // The check runs in the background. Its result is published after the link, which starts the sync of the member.
        repeat(100) {
            val result = streamEvents().filterIsInstance<VerificationCheckedEvent>().singleOrNull { it.code == code }?.result
            if (result != null) return assertEquals(CheckVerificationResponse.Linked(username), result)
            Thread.sleep(50)
        }
        fail("no result for the check of $code")
    }

    private fun grantedRoles(
        event: BitsEvent,
        discordUserId: String,
    ) = (event as RoleSyncEvent)
        .members[discordUserId]
        ?.grants
        .orEmpty()
        .mapTo(HashSet()) { it.roleId }

    /** Syncs run in the background, one after the other; waits for them and expects exactly [count] role syncs. */
    private fun awaitEvents(count: Int): List<BitsEvent> {
        awaitBackgroundWork()
        return roleSyncEvents().also { assertEquals(count, it.size, "expected $count role syncs, got $it") }
    }

    /** A link starts a sync through the registry, and the sync publishes its result through it again. */
    private fun awaitBackgroundWork() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
    }

    private fun roleSyncEvents() = streamEvents().filterIsInstance<RoleSyncEvent>()

    private fun streamEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }

    /**
     * Luftiger (w1) is a German citizen at level 30 in the Marine; Other (w2) is French, but President of Germany.
     * Each has one company for the verification.
     */
    class FakeWarera : WareraClient {
        val users = mutableMapOf<String, WareraUser>()
        val failingUsers = mutableSetOf<String>()
        var governmentDown = false
        var down = false
        var companyName = ""

        fun reset() {
            users.clear()
            users["w1"] = WareraUser("w1", "Luftiger", country = "de", mu = "m1", militaryRank = 40, leveling = WareraUser.Leveling(30))
            users["w2"] = WareraUser("w2", "Other", country = "fr", leveling = WareraUser.Leveling(3))
            failingUsers.clear()
            governmentDown = false
            down = false
            companyName = ""
        }

        override fun getAllCountries() = available { listOf(WareraCountry("de", "Germany", "de"), WareraCountry("fr", "France", "fr")) }

        override fun searchUserIds(text: String) = available { users.values.filter { it.username == text }.map { it.id } }

        override fun getUser(userId: String) =
            available {
                // WarEra changed its answer in a way BITS cannot read.
                if (userId in failingUsers) throw WareraSchemaException("unexpected response")
                users.getValue(userId)
            }

        override fun getCompanyIds(userId: String) = available { listOf("c-$userId") }

        override fun getCompany(companyId: String) = available { WareraCompany(companyId, companyName) }

        override fun getGovernment(countryId: String) =
            available {
                if (governmentDown) throw WareraApiException("down")
                WareraGovernment(country = countryId, president = "w2".takeIf { countryId == "de" })
            }

        override fun searchMilitaryUnitIds(text: String) = available { listOf("m1") }

        override fun getMilitaryUnit(muId: String) =
            available {
                if (" " in muId) throw WareraBadRequestException("invalid id")
                if (muId != "m1") throw WareraNotFoundException("no such MU")
                WareraMilitaryUnit("m1", "Deutsche Marine")
            }

        private fun <T> available(block: () -> T): T = if (down) throw WareraApiException("down") else block()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fakes {
        @Bean
        @Primary
        fun fakeWareraClient() = FakeWarera()

        @Bean
        @Primary
        fun mutableClock() = MutableClock()
    }
}
