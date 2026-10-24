package eu.luftiger.bits.backend.auth.internal

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * Denylist of session tokens that were logged out, by `jti`, each kept until the token would have expired anyway.
 * As a validator it rejects them, and tokens without an ID. If Redis cannot be asked, the session is rejected too:
 * a dashboard that needs a new login is better than one that lets a revoked session in.
 */
@Component
class RevokedSessions(
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock,
) : OAuth2TokenValidator<Jwt> {
    private val log = LoggerFactory.getLogger(javaClass)

    fun revoke(jwt: Jwt) {
        val id = jwt.id ?: return
        val remaining = Duration.between(clock.instant(), jwt.expiresAt ?: return)
        if (remaining.isNegative || remaining.isZero) return
        redisTemplate.opsForValue().set(KEY_PREFIX + id, "", remaining)
    }

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val id = token.id ?: return OAuth2TokenValidatorResult.failure(INVALID)
        val revoked =
            try {
                redisTemplate.hasKey(KEY_PREFIX + id)
            } catch (e: Exception) {
                log.error("Cannot check whether a session was revoked, rejecting it", e)
                true
            }
        return if (revoked) OAuth2TokenValidatorResult.failure(INVALID) else OAuth2TokenValidatorResult.success()
    }

    private companion object {
        const val KEY_PREFIX = "bits:auth:revoked-session:"
        val INVALID = OAuth2Error("invalid_token", "The session was ended", null)
    }
}
