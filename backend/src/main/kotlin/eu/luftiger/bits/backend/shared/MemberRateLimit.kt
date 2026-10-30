package eu.luftiger.bits.backend.shared

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration

/**
 * Limits how often a single member may start something that calls WarEra: `/verify`, `/profil`, `/rollen` and the
 * roles on "Mein Account". All WarEra calls share 100 requests per minute, so without a limit one member calling these
 * again and again could hold up the verification and the role sync of everybody else. The count is per member and
 * minute of the [Clock], shared by all these actions, and kept in Redis. Admin pages are not limited.
 *
 * Without Redis, nobody is limited: the limit protects WarEra's budget, and an outage of Redis must not lock out members.
 */
@Component
class MemberRateLimit(
    private val redis: StringRedisTemplate,
    private val properties: MemberRateLimitProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** @throws TooManyRequestsException if the member used up their actions of this minute */
    fun check(discordUserId: String) {
        val minute = clock.millis() / WINDOW.toMillis()
        val key = "$KEY_PREFIX$discordUserId:$minute"
        val count =
            try {
                redis.opsForValue().increment(key).also { if (it == 1L) redis.expire(key, WINDOW.multipliedBy(2)) }
            } catch (e: RuntimeException) {
                log.warn("Member rate limit not checked, Redis is not reachable", e)
                return
            } ?: return
        if (count > properties.perMinute) {
            log.info("Member {} reached the rate limit", discordUserId)
            throw TooManyRequestsException(WINDOW.seconds - (clock.millis() / 1000) % WINDOW.seconds)
        }
    }

    companion object {
        const val KEY_PREFIX = "bits:member-limit:"
        private val WINDOW: Duration = Duration.ofMinutes(1)
    }
}

@ConfigurationProperties("bits.member-rate-limit")
data class MemberRateLimitProperties(
    /** Actions per member and minute that call WarEra. */
    val perMinute: Int = 6,
)

/** 429 with `Retry-After`, so the bot can tell the member how long to wait. */
class TooManyRequestsException(
    val retryAfterSeconds: Long,
) : ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS) {
    override fun getHeaders(): HttpHeaders = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString()) }
}
