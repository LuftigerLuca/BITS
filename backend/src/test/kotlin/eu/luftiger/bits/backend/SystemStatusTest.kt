package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.backend.systemstatus.internal.ComponentProbe
import eu.luftiger.bits.shared.BackendCallCount
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.BotHeartbeat
import eu.luftiger.bits.shared.DiscordRequestCount
import eu.luftiger.bits.shared.EventStream
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
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
import kotlin.test.assertTrue

@SpringBootTest(properties = ["bits.service-token=$SERVICE_TOKEN", "bits.version=abc1234"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class SystemStatusTest {
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
    lateinit var probe: ComponentProbe

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
    }

    @Test
    fun `only admins see the system status`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/system-status") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/system-status").andExpect { status { isUnauthorized() } }
        status().andExpect { status { isOk() } }
    }

    @Test
    fun `postgres and redis are shown as up with what they hold, the backend with its version`() {
        probe.probe()

        val status = status()

        assertEquals(true, status.read<Boolean>("$.components[?(@.component == 'POSTGRES')].up", single = true))
        assertEquals(true, status.read<Boolean>("$.components[?(@.component == 'REDIS')].up", single = true))
        assertTrue(status.read<Number>("$.postgres.sizeBytes").toLong() > 0)
        assertTrue(status.read<String>("$.postgres.schemaVersion").isNotBlank())
        assertTrue(status.read<Number>("$.redis.usedMemoryBytes").toLong() > 0)
        assertEquals("abc1234", status.read("$.backend.version"))
    }

    @Test
    fun `the bot is online with its heartbeat and offline once it misses three`() {
        heartbeat(heartbeat()).andExpect { status { isOk() } }

        var status = status()
        assertEquals(true, status.read<Boolean>("$.components[?(@.component == 'BOT')].up", single = true))
        assertEquals("CONNECTED", status.read("$.bot.gatewayStatus"))
        assertEquals("bot1234", status.read("$.bot.process.version"))

        clock.advance(Duration.ofSeconds(91))
        status = status()
        assertEquals(false, status.read<Boolean>("$.components[?(@.component == 'BOT')].up", single = true))
        assertEquals(
            clock.millis() - 91_000,
            status.read<Number>("$.components[?(@.component == 'BOT')].checkedAt", single = true).toLong(),
        )
    }

    @Test
    fun `the counts of the bot go into the history as the difference between two heartbeats`() {
        val startedAt = clock.millis() + 7
        heartbeat(heartbeat(startedAt, unreachable = 2, refused = 1))
        val before = status()
        val failuresBefore = before.read<List<Number>>("$.bot.failureHistory").sumOf { it.toLong() }
        val discordBefore = before.read<List<Number>>("$.bot.discordHistory").sumOf { it.toLong() }

        heartbeat(heartbeat(startedAt, unreachable = 5, refused = 4))

        val after = status()
        assertEquals(3, after.read<List<Number>>("$.bot.failureHistory").sumOf { it.toLong() } - failuresBefore)
        // Three more refused and three more in total.
        assertEquals(6, after.read<List<Number>>("$.bot.discordHistory").sumOf { it.toLong() } - discordBefore)
        assertEquals(5, after.read<Number>("$.bot.backendCalls[0].unreachable").toLong())
    }

    @Test
    fun `requests of the bot are counted by endpoint, rejected ones without`() {
        heartbeat(heartbeat())
        mockMvc.get("/api/bot-presence").andExpect { status { isUnauthorized() } }

        val endpoints = status().read<List<String>>("$.api.endpoints[*].endpoint")

        assertTrue("POST /api/bot-heartbeat" in endpoints, "$endpoints")
        assertTrue("GET *" in endpoints, "$endpoints")
    }

    @Test
    fun `syncs show up as jobs, and the events they publish by type with the stream of the bot`() {
        // The group of the bot, which does not run in the tests; it may be left from an earlier test.
        runCatching { redisTemplate.opsForStream<String, String>().createGroup(EventStream.KEY, EventStream.BOT_GROUP) }
        mockMvc.put("/web/admin/bot-presence") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"IDLE","activity":null}"""
        }
        syncTrigger.syncAllWithCachedData()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        probe.probe()

        val status = status()

        assertTrue(status.read<Number>("$.jobs[?(@.job == 'ROLE_SYNC')].runs", single = true).toLong() > 0)
        assertEquals("HOURLY", status.read("$.jobs[?(@.job == 'ROLE_SYNC')].lastTrigger", single = true))
        assertTrue("BotPresenceChangedEvent" in status.read<List<String>>("$.events.published[*].type"))
        assertEquals(0, status.read<Number>("$.events.outboxPending").toLong())
        // No bot reads in the tests, so the event of the presence is still waiting for it.
        assertTrue(status.read<Number>("$.events.groups[?(@.name == '${EventStream.BOT_GROUP}')].lag", single = true).toLong() >= 1)
    }

    private fun status(): ResultActionsDsl = mockMvc.get("/web/admin/system-status") { cookie(admin) }.andExpect { status { isOk() } }

    private fun heartbeat(heartbeat: BotHeartbeat): ResultActionsDsl =
        mockMvc.post("/api/bot-heartbeat") {
            header("Authorization", "Bearer $SERVICE_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = BitsJson.encodeToString(BotHeartbeat.serializer(), heartbeat)
        }

    private fun heartbeat(
        startedAt: Long = clock.millis(),
        unreachable: Long = 0,
        refused: Long = 0,
    ) = BotHeartbeat(
        version = "bot1234",
        startedAt = startedAt,
        gatewayStatus = "CONNECTED",
        gatewayPingMillis = 42,
        guildMembers = 10,
        heapUsedBytes = 1,
        heapMaxBytes = 2,
        threads = 3,
        backendCalls = listOf(BackendCallCount("POST /api/discord-members/joined", 1, 0, 0, unreachable)),
        discordRequests = DiscordRequestCount(total = 10 + refused, rateLimited = 0, refused = refused, serverErrors = 0),
    )

    /** A filter with `[?(...)]` always yields a list; [single] takes its only element. */
    private fun <T> ResultActionsDsl.read(
        path: String,
        single: Boolean = false,
    ): T {
        val value: Any? = JsonPath.read(andReturn().response.contentAsString, path)
        @Suppress("UNCHECKED_CAST")
        return (if (single) (value as List<*>).single() else value) as T
    }

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val SERVICE_TOKEN = "test-service-token-at-least-32-chars"
