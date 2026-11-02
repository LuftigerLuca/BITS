package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.backend.systemlog.api.SystemLogRecords
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.HoneypotState
import eu.luftiger.bits.shared.HoneypotWarning
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
import kotlin.test.assertFalse

@SpringBootTest(properties = ["bits.service-token=$HONEYPOT_SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class HoneypotTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var records: SystemLogRecords

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        jdbc.execute("TRUNCATE honeypot_setting, audit_entry, system_log_entry CASCADE")
        jdbc.execute("DELETE FROM discord_channel_viewer_role")
        jdbc.execute("DELETE FROM discord_channel")
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        reportChannels()
    }

    @Test
    fun `there is no honeypot until an admin sets one`() {
        assertEquals(HoneypotState(), state())
        val view = mockMvc.get("/web/admin/honeypot") { cookie(admin) }
        assertEquals(null, view.read<String?>("$.channelId"))
        assertEquals(listOf("falle", "gesperrt"), view.read("$.channels[*].name"))
        assertEquals(listOf(true, false), view.read("$.channels[*].usable"))
    }

    @Test
    fun `an admin sets and removes the channel, which reaches the bot and the audit log`() {
        set("c-1").andExpect { status { isNoContent() } }
        assertEquals("c-1", state().channelId)

        set(null).andExpect { status { isNoContent() } }
        assertEquals(null, state().channelId)

        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("HONEYPOT_CHANNEL_CHANGED", "HONEYPOT_CHANNEL_CHANGED"), log.read("$.entries[*].action"))
        assertEquals(setOf("Honeypot-Kanal: #falle", "Honeypot-Kanal entfernt"), log.read<List<String>>("$.entries[*].description").toSet())
    }

    @Test
    fun `saving the same channel again records nothing`() {
        set("c-1")
        set("c-1").andExpect { status { isNoContent() } }

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry", Int::class.java))
    }

    @Test
    fun `a channel the bot cannot use or that does not exist is refused`() {
        set("c-2").andExpect { status { isUnprocessableContent() } }
        set("c-9").andExpect { status { isBadRequest() } }

        assertEquals(null, state().channelId)
    }

    @Test
    fun `only admins see and set the honeypot, the bot needs the service token`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/honeypot") { cookie(user) }.andExpect { status { isForbidden() } }
        set("c-1", user).andExpect { status { isForbidden() } }
        mockMvc.post("/web/admin/honeypot/resume") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/honeypot").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the bot keeps its warning in the state`() {
        bot("/warning", """{"warning":{"channelId":"c-1","messageId":"77"}}""", put = true).andExpect { status { isNoContent() } }
        assertEquals(HoneypotWarning("c-1", "77"), state().warning)

        bot("/warning", """{"warning":null}""", put = true).andExpect { status { isNoContent() } }
        assertEquals(null, state().warning)
    }

    @Test
    fun `a pause stays until an admin lifts it, which the audit log records`() {
        set("c-1")

        pause("c-1").andExpect { status { isNoContent() } }
        assertEquals(true, state().kicksPaused)
        assertEquals(listOf("HONEYPOT_KICKS_PAUSED"), logEntries("$.entries[*].type"))

        pause("c-1")
        assertEquals(listOf("HONEYPOT_KICKS_PAUSED"), logEntries("$.entries[*].type"))

        mockMvc.post("/web/admin/honeypot/resume") { cookie(admin) }.andExpect { status { isNoContent() } }
        assertFalse(state().kicksPaused)
        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals("HONEYPOT_KICKS_RESUMED", log.read<List<String>>("$.entries[*].action").first())
    }

    @Test
    fun `a new channel lifts a pause, and a late report about the old one changes nothing`() {
        set("c-1")
        pause("c-1")
        reportChannels(usable = listOf("c-1", "c-2"))

        set("c-2")
        assertFalse(state().kicksPaused)

        pause("c-1")
        assertFalse(state().kicksPaused)
    }

    @Test
    fun `kicks reach the system log and the export of the member, without any message content`() {
        kick("""{"discordUserId":"100","memberName":"Spammer","outcome":"KICKED"}""").andExpect { status { isNoContent() } }
        kick("""{"discordUserId":"101","memberName":"Boss","outcome":"SKIPPED","reason":"Über der Rolle."}""")
        kick("""{"discordUserId":"102","memberName":"Fest","outcome":"FAILED","reason":"Abgelehnt."}""")

        assertEquals(
            listOf("HONEYPOT_KICK_FAILED", "HONEYPOT_KICK_SKIPPED", "HONEYPOT_KICKED"),
            logEntries("$.entries[*].type"),
        )
        assertEquals(
            "Spammer wegen einer Nachricht im Honeypot-Kanal gekickt.",
            logEntries<String>("$.entries[*].message").last(),
        )
        assertEquals(listOf("HONEYPOT_KICKED"), records.recordsAbout("100").entries.map { it.kind })
    }

    @Test
    fun `malformed reports from the bot are refused`() {
        kick("""{"discordUserId":"abc","memberName":"x","outcome":"KICKED"}""").andExpect { status { isBadRequest() } }
        kick("""{"discordUserId":"1","memberName":"${"x".repeat(101)}","outcome":"KICKED"}""").andExpect { status { isBadRequest() } }
        bot("/warning", """{"warning":{"channelId":"c-1","messageId":"abc"}}""", put = true).andExpect { status { isBadRequest() } }
        bot("/problems", """{"reason":"${"x".repeat(1001)}"}""").andExpect { status { isBadRequest() } }
    }

    private fun reportChannels(usable: List<String> = listOf("c-1")) {
        val channels =
            listOf("c-1" to "falle", "c-2" to "gesperrt").mapIndexed { index, (id, name) ->
                """{"id":"$id","name":"$name","category":null,"position":$index,"postable":true,"honeypotUsable":${id in usable}}"""
            }
        mockMvc
            .put("/api/discord-channels") {
                header("Authorization", "Bearer $HONEYPOT_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = """{"channels":[${channels.joinToString(",")}]}"""
            }.andExpect { status { is2xxSuccessful() } }
    }

    private fun set(
        channelId: String?,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.put("/web/admin/honeypot") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = """{"channelId":${channelId?.let { "\"$it\"" }}}"""
        }

    private fun pause(channelId: String) = bot("/pause", """{"channelId":"$channelId","kickCount":10,"windowMinutes":10}""")

    private fun kick(body: String) = bot("/kicks", body)

    private fun bot(
        path: String,
        body: String,
        put: Boolean = false,
    ): ResultActionsDsl {
        val url = "/api/honeypot$path"
        return if (put) {
            mockMvc.put(url) {
                header("Authorization", "Bearer $HONEYPOT_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        } else {
            mockMvc.post(url) {
                header("Authorization", "Bearer $HONEYPOT_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }
    }

    private fun state(): HoneypotState =
        BitsJson.decodeFromString(
            mockMvc
                .get("/api/honeypot") { header("Authorization", "Bearer $HONEYPOT_SERVICE_TOKEN") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun <T> logEntries(path: String): List<T> = mockMvc.get("/web/admin/system-log") { cookie(admin) }.read(path)

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val HONEYPOT_SERVICE_TOKEN = "test-service-token-at-least-32-chars"
