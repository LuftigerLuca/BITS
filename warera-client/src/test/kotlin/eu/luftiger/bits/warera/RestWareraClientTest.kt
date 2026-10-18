package eu.luftiger.bits.warera

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RestWareraClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: RestWareraClient

    @BeforeTest
    fun setUp() {
        val builder = RestClient.builder().baseUrl("https://warera.test/trpc")
        server = MockRestServiceServer.bindTo(builder).build()
        client = RestWareraClient(builder.build(), retry = RestWareraClient.defaultRetry(Duration.ofMillis(1)))
    }

    @Test
    fun `unwraps the tRPC envelope and ignores unknown fields`() {
        server
            .expect(requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(
                withSuccess(
                    """{"result":{"data":[{"_id":"c1","name":"Germany","code":"de","money":1.5}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(listOf(WareraCountry(id = "c1", name = "Germany", code = "de")), client.getAllCountries())
        server.verify()
    }

    @Test
    fun `retries when rate limited`() {
        server
            .expect(requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server
            .expect(requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(withSuccess("""{"result":{"data":[]}}""", MediaType.APPLICATION_JSON))

        assertEquals(emptyList(), client.getAllCountries())
        server.verify()
    }

    @Test
    fun `gives up after the last attempt`() {
        server
            .expect(times(3), requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        assertFailsWith<WareraApiException> { client.getAllCountries() }
        server.verify()
    }

    @Test
    fun `does not retry client errors`() {
        server
            .expect(times(1), requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<WareraApiException> { client.getAllCountries() }
        server.verify()
    }
}
