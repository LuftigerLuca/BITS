package eu.luftiger.bits.backend.externalapi.internal

import eu.luftiger.bits.backend.shared.ForbiddenException
import eu.luftiger.bits.backend.shared.TooManyRequestsException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Clock
import java.time.Duration

/**
 * Runs before every endpoint of the third-party API, once [ApiTokenFilter] has found the token: counts the request
 * against the token's limit and checks that the token has the scope of the endpoint, see [ExternalEndpoint]. An
 * endpoint without a scope is denied, so a new one is never open by mistake. Both throw `DomainException`s, which
 * `ApiExceptionHandler` answers like any other error.
 */
@Component
class ExternalApiInterceptor(
    private val rateLimit: ApiTokenRateLimit,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        // Unknown paths and methods are left to Spring MVC, which answers 404 or 405.
        if (handler !is HandlerMethod) return true
        val authentication =
            SecurityContextHolder.getContext().authentication as? ApiTokenAuthentication
                ?: throw ForbiddenException("No API token")
        rateLimit.check(authentication.token.id)
        val scope =
            handler.getMethodAnnotation(ExternalEndpoint::class.java)?.scope
                ?: throw ForbiddenException("Endpoint is not part of the API")
        if (scope !in authentication.token.scopes) throw ForbiddenException("Token lacks the scope ${scope.id}")
        return true
    }
}

@Configuration(proxyBeanMethods = false)
class ExternalApiWebConfig(
    private val interceptor: ExternalApiInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(interceptor).addPathPatterns("/external/v1/**")
    }
}

/**
 * Limits each API token to [ExternalApiProperties.requestsPerMinute] requests per minute of the [Clock], counted in
 * Redis, so that a faulty client cannot keep the backend and the database busy.
 *
 * Without Redis, nobody is limited, like with `MemberRateLimit`: the API reads only from the database, and an outage
 * of Redis must not lock out every client.
 */
@Component
class ApiTokenRateLimit(
    private val redis: StringRedisTemplate,
    private val properties: ExternalApiProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** @throws TooManyRequestsException if the token used up the requests of this minute */
    fun check(tokenId: Long) {
        val minute = clock.millis() / WINDOW.toMillis()
        val key = "$KEY_PREFIX$tokenId:$minute"
        val count =
            try {
                redis.opsForValue().increment(key).also { if (it == 1L) redis.expire(key, WINDOW.multipliedBy(2)) }
            } catch (e: RuntimeException) {
                log.warn("API token rate limit not checked, Redis is not reachable", e)
                return
            } ?: return
        if (count > properties.requestsPerMinute) {
            if (count == properties.requestsPerMinute + 1L) log.info("API token {} reached the rate limit", tokenId)
            throw TooManyRequestsException(WINDOW.seconds - (clock.millis() / 1000) % WINDOW.seconds)
        }
    }

    companion object {
        const val KEY_PREFIX = "bits:api-token-limit:"
        private val WINDOW: Duration = Duration.ofMinutes(1)
    }
}

@ConfigurationProperties("bits.external-api")
data class ExternalApiProperties(
    /** Requests per API token and minute. */
    val requestsPerMinute: Int = 60,
)
