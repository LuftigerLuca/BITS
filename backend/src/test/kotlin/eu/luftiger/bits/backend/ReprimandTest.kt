package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.FeatureStates
import eu.luftiger.bits.shared.ProtectedCause
import eu.luftiger.bits.shared.ReprimandResponse
import eu.luftiger.bits.shared.ReprimandTimeoutEvent
import eu.luftiger.bits.shared.decodeEvent
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@SpringBootTest(properties = ["bits.service-token=$REPRIMAND_SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class ReprimandTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        jdbc.execute(
            "TRUNCATE reprimand, reprimand_role, reprimand_setting, feature_setting, discord_role, audit_entry, " +
                "system_log_entry, system_log_item, event_publication",
        )
        redisTemplate.delete(EventStream.KEY)
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        mockMvc
            .put("/api/discord-roles") {
                header("Authorization", "Bearer $REPRIMAND_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"roles":[{"id":"r-mod","name":"Moderation","color":null,"position":2,"assignable":true},
                        {"id":"r-citizen","name":"Bürger","color":null,"position":1,"assignable":true}]}"""
            }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `reprimands start switched off`() {
        assertEquals(ReprimandResponse.Disabled, reprimand(actor(administrator = true), target()))
        val states =
            mockMvc
                .get("/api/features") { bearer() }
                .andReturn()
                .response.contentAsString
        assertEquals(false, BitsJson.decodeFromString<FeatureStates>(states).reprimands)
        assertEquals(0, count("reprimand"))
    }

    @Test
    fun `an admin reprimands a member, who gets the set timeout and a count that grows`() {
        enable()

        val first = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target(), " Spam "))
        clock.advance(Duration.ofMinutes(1))
        val second = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))

        assertEquals(1, first.count)
        assertEquals(2, second.count)
        assertEquals(10, second.timeoutMinutes)
        assertEquals(Instant.parse("2026-01-01T12:11:00Z").toEpochMilli(), second.timeoutUntil)
        assertEquals("Spam", jdbc.queryForObject("SELECT reason FROM reprimand WHERE id = ?", String::class.java, first.reprimandId))
        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("REPRIMAND_GIVEN", "REPRIMAND_GIVEN"), log.read("$.entries[*].action"))
        assertEquals("Spammer gerügt (1. Rüge, Timeout 10 Minuten), Grund: Spam", log.read("$.entries[1].description"))
        assertEquals(listOf("Mod"), log.read<List<String>>("$.entries[*].actor.name").distinct())
    }

    @Test
    fun `only admins and members of the chosen roles may reprimand`() {
        enable()

        assertEquals(ReprimandResponse.Forbidden, reprimand(actor(roles = listOf("r-mod")), target()))
        setSettings("""{"roleIds":["r-mod"],"timeoutMinutes":60}""").andExpect { status { isNoContent() } }

        val reprimanded = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(roles = listOf("r-mod")), target()))
        assertEquals(60, reprimanded.timeoutMinutes)
        assertEquals(ReprimandResponse.Forbidden, reprimand(actor(roles = listOf("r-citizen")), target()))
        assertEquals(1, count("reprimand"))
    }

    @Test
    fun `nobody reprimands themselves, a bot, an admin or a member who may reprimand`() {
        enable()
        setSettings("""{"roleIds":["r-mod"],"timeoutMinutes":10}""")
        val actor = actor(administrator = true)

        assertEquals(ReprimandResponse.Protected(ProtectedCause.SELF), reprimand(actor, target(id = "7")))
        assertEquals(ReprimandResponse.Protected(ProtectedCause.BOT), reprimand(actor, target(bot = true)))
        assertEquals(ReprimandResponse.Protected(ProtectedCause.ADMIN), reprimand(actor, target(administrator = true)))
        assertEquals(ReprimandResponse.Protected(ProtectedCause.REPRIMANDER), reprimand(actor, target(roles = listOf("r-mod"))))
        assertEquals(0, count("reprimand"))
    }

    @Test
    fun `withdrawing a reprimand lowers the count and lifts its running timeout`() {
        enable()
        val reprimanded = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))

        withdraw(reprimanded.reprimandId).andExpect { status { isNoContent() } }
        withdraw(reprimanded.reprimandId).andExpect { status { isNoContent() } }

        val view = mockMvc.get("/web/admin/reprimands") { cookie(admin) }.andExpect { status { isOk() } }
        assertEquals(listOf("admin"), view.read("$.reprimands[*].withdrawnBy"))
        assertEquals(emptyList<Any>(), view.read("$.counts"))
        val next = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))
        assertEquals(1, next.count)
        jdbc.awaitOutbox()
        assertEquals(listOf(ReprimandTimeoutEvent("100", null, clock.millis())), streamEvents())
        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("REPRIMAND_GIVEN", "REPRIMAND_WITHDRAWN", "REPRIMAND_GIVEN"), log.read("$.entries[*].action"))
        assertEquals("Rüge für Spammer vom 01.01.2026 zurückgenommen, Grund war: Spam", log.read("$.entries[1].description"))
        withdraw(999).andExpect { status { isNotFound() } }
    }

    @Test
    fun `withdrawing keeps the timeout of other reprimands and leaves an ended one alone`() {
        enable()
        val first = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))
        clock.advance(Duration.ofMinutes(5))
        val second = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))

        // The newer one ends later, so withdrawing the older one changes nothing.
        withdraw(first.reprimandId)
        // Now the older one is withdrawn as well, so the timeout ends.
        withdraw(second.reprimandId)
        clock.advance(Duration.ofHours(1))
        val third = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))
        clock.advance(Duration.ofHours(1))
        withdraw(third.reprimandId)

        jdbc.awaitOutbox()
        assertEquals(listOf(null), streamEvents().map { it.until })
    }

    @Test
    fun `a newer reprimand that ends earlier is what remains after withdrawing a longer one`() {
        enable()
        setSettings("""{"roleIds":[],"timeoutMinutes":60}""")
        val long = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))
        setSettings("""{"roleIds":[],"timeoutMinutes":10}""")
        val short = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))

        withdraw(long.reprimandId)

        jdbc.awaitOutbox()
        assertEquals(listOf(short.timeoutUntil), streamEvents().map { it.until })
    }

    @Test
    fun `a timeout the bot could not set goes to the system log and is not lifted later`() {
        enable()
        val reprimanded = assertIs<ReprimandResponse.Reprimanded>(reprimand(actor(administrator = true), target()))

        bot(
            "/api/reprimands/timeout-problems",
            """{"discordUserId":"100","memberName":"Spammer","reason":"Steht über dem Bot.","reprimandId":${reprimanded.reprimandId}}""",
        ).andExpect { status { isNoContent() } }
        withdraw(reprimanded.reprimandId)

        val entries = mockMvc.get("/web/admin/system-log?category=ERROR") { cookie(admin) }
        assertEquals(listOf("REPRIMAND_TIMEOUT_FAILED"), entries.read("$.entries[*].type"))
        assertEquals(listOf("Spammer"), entries.read("$.entries[*].member.name"))
        jdbc.awaitOutbox()
        assertEquals(emptyList(), streamEvents())
    }

    @Test
    fun `admins see all reprimands and the count per member`() {
        enable()
        reprimand(actor(administrator = true), target())
        reprimand(actor(administrator = true), target(id = "101", name = "Troll"), "Beleidigung")
        reprimand(actor(administrator = true), target(name = "Spammer2"), "Werbung")

        val view = mockMvc.get("/web/admin/reprimands") { cookie(admin) }.andExpect { status { isOk() } }

        assertEquals(listOf("Werbung", "Beleidigung", "Spam"), view.read("$.reprimands[*].reason"))
        assertEquals(listOf("Mod"), view.read<List<String>>("$.reprimands[*].actorName").distinct())
        assertEquals(listOf("Spammer2", "Troll"), view.read("$.counts[*].memberName"))
        assertEquals(listOf(2, 1), view.read("$.counts[*].count"))
    }

    @Test
    fun `admins set roles and timeout, which the audit log records once`() {
        setSettings("""{"roleIds":["r-mod"],"timeoutMinutes":1440}""").andExpect { status { isNoContent() } }
        setSettings("""{"roleIds":["r-mod"],"timeoutMinutes":1440}""").andExpect { status { isNoContent() } }

        val view = mockMvc.get("/web/admin/reprimands/settings") { cookie(admin) }.andExpect { status { isOk() } }
        assertEquals(listOf("Moderation", "Bürger"), view.read("$.roles[*].name"))
        assertEquals(listOf("r-mod"), view.read("$.roleIds"))
        assertEquals(1440, view.read("$.timeoutMinutes"))
        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("Rügen: dürfen @Moderation; Timeout 1 Tag"), log.read("$.entries[*].description"))
    }

    @Test
    fun `the timeout starts at 10 minutes and stays between 1 minute and 28 days`() {
        assertEquals(10, mockMvc.get("/web/admin/reprimands/settings") { cookie(admin) }.read("$.timeoutMinutes"))

        setSettings("""{"roleIds":[],"timeoutMinutes":0}""").andExpect { status { isBadRequest() } }
        setSettings("""{"roleIds":[],"timeoutMinutes":40321}""").andExpect { status { isBadRequest() } }
        setSettings("""{"roleIds":["r-unknown"],"timeoutMinutes":10}""").andExpect { status { isBadRequest() } }
        setSettings("""{"roleIds":[],"timeoutMinutes":40320}""").andExpect { status { isNoContent() } }
    }

    @Test
    fun `a blank or too long reason is refused`() {
        enable()

        reprimandRequest(actor(administrator = true), target(), "   ").andExpect { status { isBadRequest() } }
        reprimandRequest(actor(administrator = true), target(), "x".repeat(501)).andExpect { status { isBadRequest() } }
        assertEquals(0, count("reprimand"))
    }

    @Test
    fun `the download of a member holds the reprimands they got and gave`() {
        enable()
        reprimand(actor(administrator = true), target())
        reprimand(actor(id = "100", name = "Spammer", administrator = true), target(id = "101", name = "Troll"), "Beleidigung")
        val member = session(SessionUser.discord("100", "Spammer", "https://avatar", Role.USER))

        val export = mockMvc.get("/web/account/export") { cookie(member) }.andExpect { status { isOk() } }

        assertEquals(listOf("Spam"), export.read("$.reprimands.received[*].reason"))
        assertEquals(listOf("Mod"), export.read("$.reprimands.received[*].actorName"))
        assertEquals(listOf("Troll"), export.read("$.reprimands.given[*].memberName"))
    }

    @Test
    fun `only admins see and change reprimands, the bot needs the service token`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/reprimands") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/reprimands/settings") { cookie(user) }.andExpect { status { isForbidden() } }
        setSettings("""{"roleIds":[],"timeoutMinutes":10}""", user).andExpect { status { isForbidden() } }
        withdraw(1, user).andExpect { status { isForbidden() } }
        mockMvc.post("/api/reprimands").andExpect { status { isUnauthorized() } }
    }

    private fun enable() {
        mockMvc
            .put("/web/admin/features/REPRIMANDS") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"enabled":true}"""
            }.andExpect { status { isNoContent() } }
        // Only the entries of the tests count.
        jdbc.execute("TRUNCATE audit_entry")
    }

    private fun setSettings(
        body: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.put("/web/admin/reprimands/settings") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun withdraw(
        id: Long,
        session: Cookie = admin,
    ): ResultActionsDsl = mockMvc.post("/web/admin/reprimands/$id/withdraw") { cookie(session) }

    private fun reprimand(
        actor: String,
        target: String,
        reason: String = "Spam",
    ): ReprimandResponse =
        BitsJson.decodeFromString(
            reprimandRequest(actor, target, reason)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun reprimandRequest(
        actor: String,
        target: String,
        reason: String,
    ) = bot("/api/reprimands", """{"actor":$actor,"target":$target,"reason":"$reason"}""")

    private fun actor(
        id: String = "7",
        name: String = "Mod",
        administrator: Boolean = false,
        roles: List<String> = emptyList(),
    ) = member(id, name, administrator, roles, bot = false)

    private fun target(
        id: String = "100",
        name: String = "Spammer",
        administrator: Boolean = false,
        roles: List<String> = emptyList(),
        bot: Boolean = false,
    ) = member(id, name, administrator, roles, bot)

    private fun member(
        id: String,
        name: String,
        administrator: Boolean,
        roles: List<String>,
        bot: Boolean,
    ) = """{"discordUserId":"$id","name":"$name","avatarUrl":null,"roleIds":[${roles.joinToString(",") { "\"$it\"" }}],""" +
        """"administrator":$administrator,"bot":$bot}"""

    private fun bot(
        path: String,
        body: String,
    ): ResultActionsDsl =
        mockMvc.post(path) {
            bearer()
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.bearer() =
        header("Authorization", "Bearer $REPRIMAND_SERVICE_TOKEN")

    private fun count(table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)

    private fun streamEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }
            .filterIsInstance<ReprimandTimeoutEvent>()

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val REPRIMAND_SERVICE_TOKEN = "test-service-token-at-least-32-chars"
