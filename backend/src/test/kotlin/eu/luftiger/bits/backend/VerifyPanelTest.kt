package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.VerifyPanelMessage
import eu.luftiger.bits.shared.VerifyPanelState
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

@SpringBootTest(properties = ["bits.service-token=$VERIFY_PANEL_SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class VerifyPanelTest {
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

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        jdbc.execute("TRUNCATE verify_panel_setting, audit_entry, system_log_entry CASCADE")
        jdbc.execute("DELETE FROM discord_channel_viewer_role")
        jdbc.execute("DELETE FROM discord_channel")
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        reportChannels()
    }

    @Test
    fun `there is no channel until an admin sets one`() {
        assertEquals(VerifyPanelState(), state())
        val view = mockMvc.get("/web/admin/verify-panel") { cookie(admin) }
        assertEquals(null, view.read<String?>("$.channelId"))
        assertEquals(listOf("start", "archiv"), view.read("$.channels[*].name"))
        assertEquals(listOf(true, false), view.read("$.channels[*].usable"))
    }

    @Test
    fun `an admin sets and removes the channel, which reaches the bot and the audit log`() {
        set("c-1").andExpect { status { isNoContent() } }
        assertEquals("c-1", state().channelId)

        set(null).andExpect { status { isNoContent() } }
        assertEquals(null, state().channelId)

        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("VERIFY_PANEL_CHANNEL_CHANGED", "VERIFY_PANEL_CHANNEL_CHANGED"), log.read("$.entries[*].action"))
        assertEquals(
            setOf("Verifizierungs-Kanal: #start", "Verifizierungs-Kanal entfernt"),
            log.read<List<String>>("$.entries[*].description").toSet(),
        )
    }

    @Test
    fun `saving the same channel again records nothing`() {
        set("c-1")
        set("c-1").andExpect { status { isNoContent() } }

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry", Int::class.java))
    }

    @Test
    fun `a channel the bot cannot post in or that does not exist is refused`() {
        set("c-2").andExpect { status { isUnprocessableContent() } }
        set("c-9").andExpect { status { isBadRequest() } }

        assertEquals(null, state().channelId)
    }

    @Test
    fun `only admins see and set the channel, the bot needs the service token`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/verify-panel") { cookie(user) }.andExpect { status { isForbidden() } }
        set("c-1", user).andExpect { status { isForbidden() } }
        mockMvc.get("/api/verify-panel").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the bot keeps its message in the state, and a new channel keeps it until the bot moved it`() {
        bot("/message", """{"message":{"channelId":"c-1","messageId":"77"}}""", put = true).andExpect { status { isNoContent() } }
        assertEquals(VerifyPanelMessage("c-1", "77"), state().message)

        reportChannels(postable = listOf("c-1", "c-2"))
        set("c-2")
        assertEquals(VerifyPanelState("c-2", VerifyPanelMessage("c-1", "77")), state())

        bot("/message", """{"message":null}""", put = true).andExpect { status { isNoContent() } }
        assertEquals(null, state().message)
    }

    @Test
    fun `problems reach the system log`() {
        bot("/problems", """{"reason":"Dem Bot fehlen Rechte."}""").andExpect { status { isNoContent() } }

        val log = mockMvc.get("/web/admin/system-log") { cookie(admin) }
        assertEquals(listOf("VERIFY_PANEL_FAILED"), log.read("$.entries[*].type"))
        assertEquals(listOf("Dem Bot fehlen Rechte."), log.read("$.entries[*].message"))
    }

    @Test
    fun `malformed reports from the bot are refused`() {
        bot("/message", """{"message":{"channelId":"c-1","messageId":"abc"}}""", put = true).andExpect { status { isBadRequest() } }
        bot("/problems", """{"reason":"${"x".repeat(1001)}"}""").andExpect { status { isBadRequest() } }
    }

    private fun reportChannels(postable: List<String> = listOf("c-1")) {
        val channels =
            listOf("c-1" to "start", "c-2" to "archiv").mapIndexed { index, (id, name) ->
                """{"id":"$id","name":"$name","category":null,"position":$index,"postable":${id in postable}}"""
            }
        mockMvc
            .put("/api/discord-channels") {
                header("Authorization", "Bearer $VERIFY_PANEL_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = """{"channels":[${channels.joinToString(",")}]}"""
            }.andExpect { status { is2xxSuccessful() } }
    }

    private fun set(
        channelId: String?,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.put("/web/admin/verify-panel") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = """{"channelId":${channelId?.let { "\"$it\"" }}}"""
        }

    private fun bot(
        path: String,
        body: String,
        put: Boolean = false,
    ): ResultActionsDsl {
        val url = "/api/verify-panel$path"
        return if (put) {
            mockMvc.put(url) {
                header("Authorization", "Bearer $VERIFY_PANEL_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        } else {
            mockMvc.post(url) {
                header("Authorization", "Bearer $VERIFY_PANEL_SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }
    }

    private fun state(): VerifyPanelState =
        BitsJson.decodeFromString(
            mockMvc
                .get("/api/verify-panel") { header("Authorization", "Bearer $VERIFY_PANEL_SERVICE_TOKEN") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val VERIFY_PANEL_SERVICE_TOKEN = "test-service-token-at-least-32-chars"
