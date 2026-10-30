package eu.luftiger.bits.warera

import io.github.resilience4j.core.functions.Either
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
                withSuccess(
                    """{"result":{"data":{"_id":"u1","username":"Luftiger","country":null,"militaryRank":0,"leveling":{"level":0}}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(WareraUser(id = "u1", username = "Luftiger"), client.getUser("u1"))
        server.verify()
    }

    @Test
    fun `user carries country, military unit, rank and level`() {
        server
            .expect(requestTo("https://warera.test/trpc/user.getUserLite?input=%7B%22userId%22%3A%22u1%22%7D"))
            .andRespond(
                withSuccess(
                    """{"result":{"data":{"_id":"u1","username":"Luftiger","country":"c1","mu":"m1","militaryRank":12,""" +
                        """"leveling":{"level":30,"totalXp":5}}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            WareraUser(
                id = "u1",
                username = "Luftiger",
                country = "c1",
                mu = "m1",
                militaryRank = 12,
                leveling = WareraUser.Leveling(level = 30),
            ),
            client.getUser("u1"),
        )
        server.verify()
    }

    @Test
    fun `government lists the office holders`() {
        server
            .expect(requestTo("https://warera.test/trpc/government.getByCountryId?input=%7B%22countryId%22%3A%22c1%22%7D"))
            .andRespond(
                withSuccess(
                    """{"result":{"data":{"_id":"g1","country":"c1","president":"u1","minOfDefense":"u2",""" +
                        """"congressMembers":["u1","u3"]}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            WareraGovernment(country = "c1", president = "u1", minOfDefense = "u2", congressMembers = listOf("u1", "u3")),
            client.getGovernment("c1"),
        )
        server.verify()
    }

    @Test
    fun `military units are searched and looked up`() {
        server
            .expect(requestTo("https://warera.test/trpc/search.searchAnything?input=%7B%22searchText%22%3A%22Marine%22%7D"))
            .andRespond(withSuccess("""{"result":{"data":{"userIds":["u1"],"muIds":["m1"]}}}""", MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://warera.test/trpc/mu.getById?input=%7B%22muId%22%3A%22m1%22%7D"))
            .andRespond(
                withSuccess(
                    """{"result":{"data":{"_id":"m1","name":"Deutsche Marine","members":["u1","u2","u3"],"user":"u1","country":"c1",""" +
                        """"roles":{"managers":["u1","u2"],"commanders":["u3"]}}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(listOf("m1"), client.searchMilitaryUnitIds("Marine"))
        assertEquals(
            WareraMilitaryUnit(
                id = "m1",
                name = "Deutsche Marine",
                owner = "u1",
                roles = WareraMilitaryUnitRoles(managers = listOf("u1", "u2"), commanders = listOf("u3")),
                country = "c1",
            ),
            client.getMilitaryUnit("m1"),
        )
        server.verify()
    }

    @Test
    fun `users are loaded in batches, each entry with its own result`() {
        server
            .expect(
                requestTo(
                    "https://warera.test/trpc/user.getUserLite,user.getUserLite,user.getUserLite?batch=1&input=" +
                        "%7B%220%22%3A%7B%22userId%22%3A%22u1%22%7D%2C%221%22%3A%7B%22userId%22%3A%22u9%22%7D%2C%222%22%3A%7B%22userId%22%3A%22u2%22%7D%7D",
                ),
            ).andRespond(
                withStatus(HttpStatus.MULTI_STATUS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """[{"result":{"data":$USER_U1}},""" +
                            """{"error":{"message":"Users not found.","code":-32004,"data":{"code":"NOT_FOUND","httpStatus":404}}},""" +
                            """{"result":{"data":{"_id":"u2"}}}]""",
                    ),
            )

        val users = client.getUsers(listOf("u1", "u9", "u2", "u1"))

        assertEquals(listOf("u1", "u9", "u2"), users.keys.toList())
        assertEquals(WareraUser(id = "u1", username = "Luftiger"), users.getValue("u1").getOrThrow())
        assertTrue(users.getValue("u9").exceptionOrNull() is WareraNotFoundException)
        assertTrue(users.getValue("u2").exceptionOrNull() is WareraSchemaException)
        server.verify()
    }

    @Test
    fun `a batch in which every user fails is still read entry by entry`() {
        server
            .expect(queryParam("batch", "1"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""[{"error":{"message":"Users not found.","data":{"httpStatus":404}}}]"""),
            )

        assertTrue(client.getUsers(listOf("u9")).getValue("u9").exceptionOrNull() is WareraNotFoundException)
        server.verify()
    }

    @Test
    fun `more users than fit in one batch take several requests`() {
        val ids = (1..RestWareraClient.USER_BATCH_SIZE + 1).map { "u$it" }
        server
            .expect(times(1), queryParam("batch", "1"))
            .andRespond(
                withSuccess(
                    List(RestWareraClient.USER_BATCH_SIZE) {
                        """{"result":{"data":$USER_U1}}"""
                    }.joinToString(",", "[", "]"),
                    MediaType.APPLICATION_JSON,
                ),
            )
        server
            .expect(times(1), queryParam("batch", "1"))
            .andRespond(withSuccess("""[{"result":{"data":$USER_U1}}]""", MediaType.APPLICATION_JSON))

        assertEquals(ids.size, client.getUsers(ids).values.count { it.isSuccess })
        server.verify()
    }

    @Test
    fun `a batch is retried as a whole when rate limited`() {
        server.expect(times(1), queryParam("batch", "1")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
        server
            .expect(
                times(1),
                queryParam("batch", "1"),
            ).andRespond(withSuccess("""[{"result":{"data":$USER_U1}}]""", MediaType.APPLICATION_JSON))

        assertTrue(client.getUsers(listOf("u1")).getValue("u1").isSuccess)
        server.verify()
    }

    @Test
    fun `a batch fails as a whole when WarEra is down`() {
        server.expect(times(3), queryParam("batch", "1")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertFailsWith<WareraApiException> { client.getUsers(listOf("u1")) }
        server.verify()
    }

    @Test
    fun `a batch answer with a different number of entries is a schema error`() {
        server.expect(queryParam("batch", "1")).andRespond(withSuccess("""[{"result":{"data":$USER_U1}}]""", MediaType.APPLICATION_JSON))

        assertFailsWith<WareraSchemaException> { client.getUsers(listOf("u1", "u2")) }
    }

    @Test
    fun `unknown objects are reported as not found`() {
        server
            .expect(times(1), requestTo("https://warera.test/trpc/mu.getById?input=%7B%22muId%22%3A%22m9%22%7D"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<WareraNotFoundException> { client.getMilitaryUnit("m9") }
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
    fun `parties are collected across all pages`() {
        server
            .expect(
                requestTo(
                    "https://warera.test/trpc/party.getManyPaginated?input=%7B%22countryId%22%3A%22c1%22%2C%22limit%22%3A100%7D",
                ),
            ).andRespond(
                withSuccess(
                    """{"result":{"data":{"items":[{"_id":"p1","name":"Partei","members":["u1","u2"],"leader":"u1"}],"nextCursor":"next"}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        server
            .expect(
                requestTo(
                    "https://warera.test/trpc/party.getManyPaginated?input=%7B%22countryId%22%3A%22c1%22%2C%22limit%22%3A100%2C%22cursor%22%3A%22next%22%7D",
                ),
            ).andRespond(withSuccess("""{"result":{"data":{"items":[{"_id":"p2","name":"Leer"}]}}}""", MediaType.APPLICATION_JSON))

        assertEquals(
            listOf(WareraParty("p1", "Partei", listOf("u1", "u2"), leader = "u1"), WareraParty("p2", "Leer")),
            client.getParties("c1"),
        )
        server.verify()
    }

    @Test
    fun `company comes with its owner`() {
        server
            .expect(requestTo("https://warera.test/trpc/company.getById?input=%7B%22companyId%22%3A%22c1%22%7D"))
            .andRespond(
                withSuccess(
                    """{"result":{"data":{"_id":"c1","user":"u1","name":"BITS-ABC123","workerCount":2}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(WareraCompany(id = "c1", name = "BITS-ABC123", ownerId = "u1"), client.getCompany("c1"))
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

    @Test
    fun `answers that do not fit the schema are reported as API errors`() {
        server
            .expect(requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(withSuccess("<html>Bad Gateway</html>", MediaType.TEXT_HTML))
        server
            .expect(requestTo("https://warera.test/trpc/user.getUserLite?input=%7B%22userId%22%3A%22u1%22%7D"))
            .andRespond(withSuccess("""{"result":{"data":{"_id":"u1"}}}""", MediaType.APPLICATION_JSON))

        assertFailsWith<WareraSchemaException> { client.getAllCountries() }
        val missingField = assertFailsWith<WareraSchemaException> { client.getUser("u1") }
        assertTrue("user.getUserLite" in missingField.message.orEmpty(), missingField.message)
        server.verify()
    }

    @Test
    fun `a user without the fields the role sync judges is a schema error, except for the military unit`() {
        val complete = """"_id":"u1","username":"Luftiger","country":"c1","militaryRank":12,"leveling":{"level":30}"""
        listOf(
            complete.replace(""","country":"c1"""", ""),
            complete.replace(""","militaryRank":12""", ""),
            complete.replace(""""leveling":{"level":30}""", """"leveling":{}"""),
        ).forEach { data ->
            server.reset()
            server
                .expect(requestTo("https://warera.test/trpc/user.getUserLite?input=%7B%22userId%22%3A%22u1%22%7D"))
                .andRespond(withSuccess("""{"result":{"data":{$data}}}""", MediaType.APPLICATION_JSON))
            assertFailsWith<WareraSchemaException>(data) { client.getUser("u1") }
        }

        server.reset()
        server
            .expect(requestTo("https://warera.test/trpc/user.getUserLite?input=%7B%22userId%22%3A%22u1%22%7D"))
            .andRespond(withSuccess("""{"result":{"data":{$complete}}}""", MediaType.APPLICATION_JSON))
        assertEquals(null, client.getUser("u1").mu)
    }

    @Test
    fun `countries do not need a code`() {
        server
            .expect(requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(withSuccess("""{"result":{"data":[{"_id":"c1","name":"Germany"}]}}""", MediaType.APPLICATION_JSON))

        assertEquals(listOf(WareraCountry(id = "c1", name = "Germany")), client.getAllCountries())
    }

    @Test
    fun `countries carry their population and active population`() {
        server
            .expect(requestTo("https://warera.test/trpc/country.getAllCountries"))
            .andRespond(
                withSuccess(
                    """{"result":{"data":[{"_id":"c1","name":"Germany","currentPopulation":982,""" +
                        """"rankings":{"countryActivePopulation":{"value":925,"rank":1,"tier":"master"},"other":{}}}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val country = client.getAllCountries().single()
        assertEquals(982, country.currentPopulation)
        assertEquals(925, country.activePopulation)
    }

    @Test
    fun `rejected input is its own error and not retried`() {
        server
            .expect(times(1), requestTo("https://warera.test/trpc/mu.getById?input=%7B%22muId%22%3A%22bad%22%7D"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<WareraBadRequestException> { client.getMilitaryUnit("bad") }
        server.verify()
    }

    @Test
    fun `waits as long as a 429 asks for, up to a limit, and backs off exponentially otherwise`() {
        val interval = RestWareraClient.defaultRetry(Duration.ofSeconds(2)).retryConfig.getIntervalBiFunction<Any>()

        fun tooManyRequests(retryAfter: String?) =
            Either.left<Throwable, Any>(
                HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too Many Requests",
                    HttpHeaders().apply { retryAfter?.let { set(HttpHeaders.RETRY_AFTER, it) } },
                    ByteArray(0),
                    null,
                ),
            )

        assertEquals(5_000, interval.apply(1, tooManyRequests("5")))
        assertEquals(10_000, interval.apply(1, tooManyRequests("120")))
        assertEquals(2_000, interval.apply(1, tooManyRequests(null)))
        assertEquals(4_000, interval.apply(2, tooManyRequests(null)))
        assertEquals(4_000, interval.apply(2, Either.left<Throwable, Any>(ResourceAccessException("timeout"))))
    }

    @Test
    fun `no sliding minute gets more calls than WarEra allows`() {
        // A minute shrunk to 600 ms: periods of 20 ms instead of two seconds.
        val period = Duration.ofMillis(20)
        val minute = period.toNanos() * 30
        val limiter = RestWareraClient.defaultRateLimiter(period = period)
        val permits = mutableListOf<Long>()
        val end = System.nanoTime() + minute * 3
        while (System.nanoTime() < end) {
            if (limiter.acquirePermission()) permits += System.nanoTime()
        }

        var maxInMinute = 0
        var start = 0
        permits.forEachIndexed { i, at ->
            while (permits[start] <= at - minute) start++
            maxInMinute = maxOf(maxInMinute, i - start + 1)
        }
        assertTrue(maxInMinute <= RestWareraClient.WARERA_LIMIT_PER_MINUTE, "$maxInMinute calls in one sliding minute")
        assertTrue(maxInMinute >= 80, "only $maxInMinute calls in one sliding minute, the limiter is too strict")
    }

    private companion object {
        const val USER_U1 = """{"_id":"u1","username":"Luftiger","country":null,"militaryRank":0,"leveling":{"level":0}}"""
    }
}
