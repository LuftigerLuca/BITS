package eu.luftiger.bits.backend

import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.decodeEvent
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=test-token"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, BitsBackendApplicationTest.FakeWarera::class)
class BitsBackendApplicationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

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

    @TestConfiguration(proxyBeanMethods = false)
    class FakeWarera {
        @Bean
        @Primary
        fun fakeWareraClient() =
            object : WareraClient {
                override fun getAllCountries() = listOf(WareraCountry(id = "c1", name = "Germany", code = "de"))

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
