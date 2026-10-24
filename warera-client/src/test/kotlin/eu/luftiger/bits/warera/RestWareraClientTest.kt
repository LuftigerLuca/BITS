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
    fun `passes the input as JSON in the query string`() {
        server
            .expect(requestTo("https://warera.test/trpc/user.getUserLite?input=%7B%22userId%22%3A%22u1%22%7D"))
            .andRespond(
                withSuccess("""{"result":{"data":{"_id":"u1","username":"Luftiger","country":"c1"}}}""", MediaType.APPLICATION_JSON),
            )

        assertEquals(WareraUser(id = "u1", username = "Luftiger"), client.getUser("u1"))
        server.verify()
    }

    @Test
    fun `search returns the user ids`() {
        server
            .expect(requestTo("https://warera.test/trpc/search.searchAnything?input=%7B%22searchText%22%3A%22Luf%20tiger%22%7D"))
            .andRespond(
                withSuccess(
                    """{"result":{"data":{"userIds":["u1","u2"],"countryIds":["c1"],"hasData":true}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(listOf("u1", "u2"), client.searchUserIds("Luf tiger"))
        server.verify()
    }

    @Test
    fun `company ids are collected across all pages`() {
        server
            .expect(
                requestTo(
                    "https://warera.test/trpc/company.getCompanies?input=%7B%22userId%22%3A%22u1%22%2C%22perPage%22%3A100%7D",
                ),
            ).andRespond(withSuccess("""{"result":{"data":{"items":["a","b"],"nextCursor":"next"}}}""", MediaType.APPLICATION_JSON))
        server
            .expect(
                requestTo(
                    "https://warera.test/trpc/company.getCompanies?input=%7B%22userId%22%3A%22u1%22%2C%22perPage%22%3A100%2C%22cursor%22%3A%22next%22%7D",
                ),
            ).andRespond(withSuccess("""{"result":{"data":{"items":["c"]}}}""", MediaType.APPLICATION_JSON))

        assertEquals(listOf("a", "b", "c"), client.getCompanyIds("u1"))
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
