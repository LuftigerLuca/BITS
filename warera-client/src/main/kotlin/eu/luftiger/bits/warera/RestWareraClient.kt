package eu.luftiger.bits.warera

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
 * responses, server errors and network errors are retried by [retry].
 */
class RestWareraClient(
    private val restClient: RestClient,
    private val rateLimiter: RateLimiter = defaultRateLimiter(),
    private val retry: Retry = defaultRetry(),
) : WareraClient {
    override fun getAllCountries(): List<WareraCountry> = query("country.getAllCountries", ListSerializer(WareraCountry.serializer()))

    private fun <T> query(
        procedure: String,
        serializer: KSerializer<T>,
    ): T {
        val call =
            RateLimiter.decorateCallable(rateLimiter) {
                restClient
                    .get()
                    .uri("/{procedure}", procedure)
                    .retrieve()
                    .body(String::class.java)
                    ?: throw WareraApiException("Empty response from $procedure")
            }
        val body =
            try {
                Retry.decorateCallable(retry, call).call()
            } catch (e: RestClientException) {
                throw WareraApiException("WarEra API call $procedure failed", e)
            }
        return json.decodeFromString(TrpcResponse.serializer(serializer), body).result.data
    }

    @Serializable
    private data class TrpcResponse<T>(
        val result: TrpcResult<T>,
    )

    @Serializable
    private data class TrpcResult<T>(
        val data: T,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api2.warera.io/trpc"

        private val json = Json { ignoreUnknownKeys = true }

        fun defaultRateLimiter(): RateLimiter =
            RateLimiter.of(
                "warera",
                RateLimiterConfig
                    .custom()
                    .limitForPeriod(90)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ofSeconds(30))
                    .build(),
            )

        fun defaultRetry(waitDuration: Duration = Duration.ofSeconds(2)): Retry =
            Retry.of(
                "warera",
                RetryConfig
                    .custom<Any>()
                    .maxAttempts(3)
                    .waitDuration(waitDuration)
                    .retryOnException(::isRetryable)
                    .build(),
            )

        private fun isRetryable(e: Throwable): Boolean =
            when (e) {
                is HttpClientErrorException.TooManyRequests -> true
                is HttpServerErrorException -> true
                is ResourceAccessException -> true
                else -> false
            }
    }
}
