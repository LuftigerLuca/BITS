package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.ping.internal.PingLogCleanup
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.decodeEvent
import eu.luftiger.bits.shared.encode
import eu.luftiger.bits.warera.RestWareraClient
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCountry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient
import java.net.ServerSocket
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, BitsBackendApplicationTest.FakeWarera::class)
class BitsBackendApplicationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var restClientBuilder: RestClient.Builder

    @Autowired
    lateinit var eventPublisher: EventPublisher

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var pingLogCleanup: PingLogCleanup

    /** The containers are shared with the other test classes. */
    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        jdbc.execute("TRUNCATE ping_log, event_publication")
        redisTemplate.delete(EventStream.KEY)
    }

    @Test
    fun `health endpoint is up`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `api rejects requests without service token`() {
        mockMvc
            .post("/api/ping") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"42"}"""
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `ping is stored and published as event`() {
        mockMvc
            .post("/api/ping") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"42"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.pingCount") { value(1) }
                jsonPath("$.wareraCountryCount") { value(1) }
            }

        jdbc.awaitOutbox()
        val records =
            redisTemplate.opsForStream<String, String>().read(StreamOffset.fromStart(EventStream.KEY))!!
        val event = decodeEvent(records.single().value[EventStream.PAYLOAD_FIELD]!!)
        assertEquals("42", (event as PingEvent).discordUserId)
    }

    @Test
    fun `events of a transaction that rolls back never reach the bot`() {
        transactionTemplate.executeWithoutResult {
            eventPublisher.publish(PingEvent(discordUserId = "42", occurredAt = 0))
            it.setRollbackOnly()
        }
        eventPublisher.publish(PingEvent(discordUserId = "43", occurredAt = 0))

        jdbc.awaitOutbox()
        assertEquals(listOf("43"), streamEvents().map { (it as PingEvent).discordUserId })
    }

    @Test
    fun `the stream keeps a week of events`() {
        val now = System.currentTimeMillis()
        val stream = redisTemplate.opsForStream<String, String>()
        listOf(EventStream.RETENTION.toMillis() + 60_000, EventStream.RETENTION.toMillis() - 60_000).forEach { age ->
            stream.add(
                StreamRecords
                    .string(mapOf(EventStream.PAYLOAD_FIELD to PingEvent("old", occurredAt = 0).encode()))
                    .withStreamKey(EventStream.KEY)
                    .withId(RecordId.of(now - age, 0)),
            )
        }

        eventPublisher.publish(PingEvent(discordUserId = "new", occurredAt = 0))

        jdbc.awaitOutbox()
        assertEquals(2, stream.size(EventStream.KEY))
    }

    @Test
    fun `a WarEra call that gets no answer ends after the read timeout`() {
        // Accepts the connection, but never answers.
        ServerSocket(0).use { server ->
            val client =
                RestWareraClient(
                    restClientBuilder.baseUrl("http://localhost:${server.localPort}").build(),
                    retry = RestWareraClient.defaultRetry(Duration.ofMillis(1)),
                )

            val started = System.nanoTime()
            assertFailsWith<WareraApiException> { client.getAllCountries() }
            val elapsed = Duration.ofNanos(System.nanoTime() - started)
            assertTrue(elapsed < Duration.ofSeconds(5), "took $elapsed")
        }
    }

    @Test
    fun `malformed input from the bot is a 400 without details`() {
        val tooManyRoles = (1..251).joinToString(",") { """{"id":"$it","name":"R","color":null,"position":$it,"assignable":true}""" }
        val invalid =
            listOf(
                "/api/ping" to """{"discordUserId":"abc"}""",
                "/api/ping" to """{"discordUserId":"${"1".repeat(21)}"}""",
                "/api/verifications" to """{"discordUserId":"1","wareraUsername":"${"a".repeat(33)}"}""",
                "/api/verifications" to """{"discordUserId":"1","wareraUsername":"  "}""",
                "/api/verifications" to """{"discordUserId":"x1","wareraUsername":"Luftiger"}""",
                "/api/verifications/check" to """{"discordUserId":"1","code":"BITS-ABC"}""",
                "/api/verifications/check" to """{"discordUserId":"1 OR 1=1","code":"BITS-ABC234"}""",
                "/api/verifications/unlink" to """{"discordUserId":""}""",
            )
        invalid.forEach { (path, body) ->
            mockMvc
                .post(path) {
                    header("Authorization", "Bearer test-service-token-at-least-32-chars")
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { doesNotExist() }
                }
        }
        listOf(
            """[{"id":"1","name":"${"n".repeat(101)}","color":null,"position":1,"assignable":true}]""",
            """[{"id":"${"1".repeat(33)}","name":"R","color":null,"position":1,"assignable":true}]""",
            "[$tooManyRoles]",
        ).forEach { roles ->
            mockMvc
                .put("/api/discord-roles") {
                    header("Authorization", "Bearer test-service-token-at-least-32-chars")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"roles":$roles}"""
                }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `pings are deleted after 30 days`() {
        jdbc.update("INSERT INTO ping_log (discord_user_id, created_at) VALUES ('old', now() - interval '31 days')")
        jdbc.update("INSERT INTO ping_log (discord_user_id, created_at) VALUES ('recent', now() - interval '29 days')")

        pingLogCleanup.deleteOld()

        assertEquals(listOf("recent"), jdbc.queryForList("SELECT discord_user_id FROM ping_log", String::class.java))
    }

    private fun streamEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    @TestConfiguration(proxyBeanMethods = false)
    class FakeWarera {
        @Bean
        @Primary
        fun fakeWareraClient() =
            object : WareraClient {
                override fun getAllCountries() = listOf(WareraCountry(id = "c1", name = "Germany"))

                override fun searchUserIds(text: String) = TODO()

                override fun getUser(userId: String) = TODO()

                override fun getCompanyIds(userId: String) = TODO()

                override fun getCompany(companyId: String) = TODO()

                override fun getGovernment(countryId: String) = TODO()

                override fun searchMilitaryUnitIds(text: String) = TODO()

                override fun getMilitaryUnit(muId: String) = TODO()

                override fun getParties(countryId: String) = TODO()
            }
    }
}
