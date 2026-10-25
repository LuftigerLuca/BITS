package eu.luftiger.bits.warera

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.http.HttpHeaders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration

/**
 * [WareraClient] for the tRPC API of WarEra (`https://api2.warera.io/trpc`).
 *
 * The API allows 100 requests per minute and IP, so calls are throttled by [rateLimiter]. Rate-limit
 * responses, server errors and network errors are retried by [retry]. Timeouts come from the [RestClient].
 */
class RestWareraClient(
    private val restClient: RestClient,
    private val rateLimiter: RateLimiter = defaultRateLimiter(),
    private val retry: Retry = defaultRetry(),
) : WareraClient {
    override fun getAllCountries(): List<WareraCountry> = query("country.getAllCountries", ListSerializer(WareraCountry.serializer()))

    override fun searchUserIds(text: String): List<String> = search(text).userIds

    override fun getUser(userId: String): WareraUser =
        query("user.getUserLite", WareraUser.serializer(), buildJsonObject { put("userId", userId) })

    override fun getCompanyIds(userId: String): List<String> {
        val ids = mutableListOf<String>()
        var cursor: String? = null
        do {
            val input =
                buildJsonObject {
                    put("userId", userId)
                    put("perPage", COMPANY_PAGE_SIZE)
                    cursor?.let { put("cursor", it) }
                }
            val page = query("company.getCompanies", CompanyPage.serializer(), input)
            ids += page.items
            cursor = page.nextCursor
        } while (cursor != null)
        return ids
    }

    override fun getCompany(companyId: String): WareraCompany =
        query("company.getById", WareraCompany.serializer(), buildJsonObject { put("companyId", companyId) })

    override fun getGovernment(countryId: String): WareraGovernment =
        query("government.getByCountryId", WareraGovernment.serializer(), buildJsonObject { put("countryId", countryId) })

    override fun searchMilitaryUnitIds(text: String): List<String> = search(text).muIds

    override fun getMilitaryUnit(muId: String): WareraMilitaryUnit =
        query("mu.getById", WareraMilitaryUnit.serializer(), buildJsonObject { put("muId", muId) })

    override fun getParties(countryId: String): List<WareraParty> {
        val parties = mutableListOf<WareraParty>()
        var cursor: String? = null
        do {
            val input =
                buildJsonObject {
                    put("countryId", countryId)
                    put("limit", PARTY_PAGE_SIZE)
                    cursor?.let { put("cursor", it) }
                }
            val page = query("party.getManyPaginated", PartyPage.serializer(), input)
            parties += page.items
            cursor = page.nextCursor
        } while (cursor != null)
        return parties
    }

    private fun search(text: String): SearchResult =
        query("search.searchAnything", SearchResult.serializer(), buildJsonObject { put("searchText", text) })

    private fun <T> query(
        procedure: String,
        serializer: KSerializer<T>,
        input: JsonObject? = null,
    ): T {
        val call =
            RateLimiter.decorateCallable(rateLimiter) {
                restClient
                    .get()
                    .uri { builder ->
                        builder.path("/{procedure}")
                        // tRPC takes the input of a query as JSON in the query string.
                        if (input != null) builder.queryParam("input", "{input}")
                        builder.build(mapOf("procedure" to procedure, "input" to input?.toString().orEmpty()))
                    }.retrieve()
                    .body(String::class.java)
                    ?: throw WareraApiException("Empty response from $procedure")
            }
        val body =
            try {
                Retry.decorateCallable(retry, call).call()
            } catch (e: HttpClientErrorException.NotFound) {
                throw WareraNotFoundException("WarEra API call $procedure found nothing", e)
            } catch (e: HttpClientErrorException.BadRequest) {
                throw WareraBadRequestException("WarEra API call $procedure rejected its input", e)
            } catch (e: RestClientException) {
                throw WareraApiException("WarEra API call $procedure failed", e)
            } catch (e: RequestNotPermitted) {
                throw WareraApiException("WarEra API call $procedure was throttled for too long", e)
            }
        return try {
            json.decodeFromString(TrpcResponse.serializer(serializer), body).result.data
        } catch (e: IllegalArgumentException) {
            // Covers SerializationException. Its message quotes the response, which may hold user data, so only
            // the first line goes along, and no cause.
            throw WareraSchemaException("WarEra API call $procedure returned an unexpected response: ${e.message?.lineSequence()?.first()}")
        }
    }

    @Serializable
    private data class TrpcResponse<T>(
        val result: TrpcResult<T>,
    )

    @Serializable
    private data class TrpcResult<T>(
        val data: T,
    )

    @Serializable
    private data class SearchResult(
        val userIds: List<String> = emptyList(),
        val muIds: List<String> = emptyList(),
    )

    @Serializable
    private data class CompanyPage(
        val items: List<String>,
        val nextCursor: String? = null,
    )

    @Serializable
    private data class PartyPage(
        val items: List<WareraParty>,
        val nextCursor: String? = null,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api2.warera.io/trpc"

        /** Largest page size the API accepts. */
        private const val COMPANY_PAGE_SIZE = 100

        /** Largest page size the API accepts; Germany has about 20 parties. */
        private const val PARTY_PAGE_SIZE = 100

        private val json = Json { ignoreUnknownKeys = true }

        /** WarEra counts the requests of the last minute, not per fixed minute. */
        const val WARERA_LIMIT_PER_MINUTE = 100

        const val CALLS_PER_PERIOD = 3

        /** Longest `Retry-After` that is waited for; a longer one would outlast any request. */
        private val MAX_RETRY_AFTER: Duration = Duration.ofSeconds(10)

        /**
         * Three calls every two seconds, i.e. 90 per minute. The limiter refills per period, so a sliding minute can
         * catch 31 periods: at most 93 calls, below WarEra's 100. One large period would allow twice its limit
         * around the refill. A caller waits up to [timeout] for a permit.
         */
        fun defaultRateLimiter(
            period: Duration = Duration.ofSeconds(2),
            timeout: Duration = Duration.ofSeconds(10),
        ): RateLimiter =
            RateLimiter.of(
                "warera",
                RateLimiterConfig
                    .custom()
                    .limitForPeriod(CALLS_PER_PERIOD)
                    .limitRefreshPeriod(period)
                    .timeoutDuration(timeout)
                    .build(),
            )

        /** Waits as long as a 429 asks for, up to [MAX_RETRY_AFTER], otherwise [waitDuration], doubled per attempt. */
        fun defaultRetry(waitDuration: Duration = Duration.ofSeconds(2)): Retry =
            Retry.of(
                "warera",
                RetryConfig
                    .custom<Any>()
                    .maxAttempts(3)
                    .intervalBiFunction { attempt, result ->
                        val retryAfter = if (result.isLeft) retryAfter(result.left) else null
                        retryAfter?.toMillis() ?: (waitDuration.toMillis() shl (attempt - 1))
                    }.retryOnException(::isRetryable)
                    .build(),
            )

        private fun retryAfter(e: Throwable): Duration? =
            (e as? HttpClientErrorException.TooManyRequests)
                ?.responseHeaders
                ?.getFirst(HttpHeaders.RETRY_AFTER)
                ?.toLongOrNull()
                ?.let { minOf(Duration.ofSeconds(it), MAX_RETRY_AFTER) }

        private fun isRetryable(e: Throwable): Boolean =
            when (e) {
                is HttpClientErrorException.TooManyRequests -> true
                is HttpServerErrorException -> true
                is ResourceAccessException -> true
                else -> false
            }
    }
}
