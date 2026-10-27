package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.BotActivity
import eu.luftiger.bits.shared.BotActivityType
import eu.luftiger.bits.shared.BotPresence
import eu.luftiger.bits.shared.BotPresenceChangedEvent
import eu.luftiger.bits.shared.BotStatus
import eu.luftiger.bits.shared.EventStream
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
import org.springframework.test.web.servlet.put
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=$SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class BotPresenceTest {
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
        jdbc.execute("TRUNCATE bot_presence_setting, audit_entry, event_publication")
        redisTemplate.delete(EventStream.KEY)
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
    }

    @Test
    fun `the bot is online without activity until an admin sets something`() {
        assertEquals(BotPresence(), presence(mockMvc.get("/web/admin/bot-presence") { cookie(admin) }))
        assertEquals(BotPresence(), botPresence())
    }

    @Test
    fun `an admin sets status and activity, which reaches the bot and the audit log`() {
        set("""{"status":"IDLE","activity":{"type":"WATCHING","text":"  WarEra "}}""").andExpect { status { isOk() } }

        val expected = BotPresence(BotStatus.IDLE, BotActivity(BotActivityType.WATCHING, "WarEra"))
        assertEquals(expected, presence(mockMvc.get("/web/admin/bot-presence") { cookie(admin) }))
        assertEquals(expected, botPresence())
        jdbc.awaitOutbox()
        assertEquals(listOf(BotPresenceChangedEvent(expected, clock.millis())), streamEvents())
        val log = mockMvc.get("/web/admin/audit-log") { cookie(admin) }
        assertEquals(listOf("BOT_PRESENCE_CHANGED"), log.read("$.entries[*].action"))
        assertEquals(listOf("Bot-Status: Abwesend, Schaut WarEra"), log.read("$.entries[*].description"))
    }

    @Test
    fun `the activity can be removed again`() {
        set("""{"status":"ONLINE","activity":{"type":"CUSTOM","text":"Wartung"}}""")

        set("""{"status":"DO_NOT_DISTURB","activity":null}""").andExpect { status { isOk() } }

        assertEquals(BotPresence(BotStatus.DO_NOT_DISTURB), botPresence())
    }

    @Test
    fun `blank, too long or unknown activities are rejected and change nothing`() {
        set("""{"status":"ONLINE","activity":{"type":"PLAYING","text":"   "}}""").andExpect { status { isBadRequest() } }
        set("""{"status":"ONLINE","activity":{"type":"PLAYING","text":"${"x".repeat(129)}"}}""").andExpect { status { isBadRequest() } }
        set("""{"status":"ONLINE","activity":{"type":"STREAMING","text":"WarEra"}}""").andExpect { status { isBadRequest() } }
        set("""{"status":"AWAY"}""").andExpect { status { isBadRequest() } }

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bot_presence_setting", Int::class.java))
        assertEquals(emptyList(), streamEvents())
    }

    @Test
    fun `only admins see and set the presence, the bot needs the service token`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/bot-presence") { cookie(user) }.andExpect { status { isForbidden() } }
        set("""{"status":"IDLE"}""", user).andExpect { status { isForbidden() } }
        mockMvc.get("/api/bot-presence").andExpect { status { isUnauthorized() } }
    }

    private fun set(
        body: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.put("/web/admin/bot-presence") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun botPresence(): BotPresence =
        presence(mockMvc.get("/api/bot-presence") { header("Authorization", "Bearer $SERVICE_TOKEN") }.andExpect { status { isOk() } })

    private fun presence(result: ResultActionsDsl): BotPresence = BitsJson.decodeFromString(result.andReturn().response.contentAsString)

    private fun streamEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val SERVICE_TOKEN = "test-service-token-at-least-32-chars"
