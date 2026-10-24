package eu.luftiger.bits.backend

import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.decodeEvent
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
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.client.RestClient
import java.net.ServerSocket
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest(properties = ["bits.service-token=test-token"])
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

    /** The containers are shared with the other test classes. */
    @BeforeTest
    fun reset() {
        jdbc.execute("TRUNCATE ping_log")
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
                header("Authorization", "Bearer test-token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"42"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.pingCount") { value(1) }
                jsonPath("$.wareraCountryCount") { value(1) }
            }

        val records =
            redisTemplate.opsForStream<String, String>().read(StreamOffset.fromStart(EventStream.KEY))!!
        val event = decodeEvent(records.single().value[EventStream.PAYLOAD_FIELD]!!)
        assertEquals("42", (event as PingEvent).discordUserId)
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
            }
    }
}
