package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The server side of the dashboard sessions, one hash per login in Redis with the session ([StoredSession]) and the
 * time of its last request. A session ends [IDLE_TIMEOUT] after its last request, and [MAX_LIFETIME] after the login
 * however active it is; every request with one of its tokens counts. Both are measured with the injected [Clock];
 * the TTL of the hash only clears away what nobody uses any more.
 *
 * As a validator it rejects tokens of sessions that have ended: by logout, by a failed check at Discord, or by
 * running out. Tokens without a session ID, issued before sessions were kept, pass and simply expire. If Redis cannot
 * be asked, the token is rejected, like a revoked one.
 */
@Component
class SessionStore(
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock,
) : OAuth2TokenValidator<Jwt> {
    private val log = LoggerFactory.getLogger(javaClass)

    /** The session, as long as it has not been idle for too long. */
    fun find(id: String): StoredSession? {
        val fields = redisTemplate.opsForHash<String, String>().multiGet(KEY_PREFIX + id, listOf(DATA_FIELD, LAST_SEEN_FIELD))
        val data = fields[0] ?: return null
        val lastSeen = fields[1]?.toLongOrNull() ?: return null
        if (clock.millis() - lastSeen > IDLE_TIMEOUT.toMillis()) return null
        return json.decodeFromString(StoredSession.serializer(), data)
    }

    /** Writes the session and counts it as used now. */
    fun save(
        id: String,
        session: StoredSession,
    ) {
        val ttl = ttl(Instant.ofEpochSecond(session.expiresAt)) ?: return delete(id)
        redisTemplate.execute(
            SAVE_SCRIPT,
            listOf(KEY_PREFIX + id),
            json.encodeToString(StoredSession.serializer(), session),
            clock.millis().toString(),
            ttl.toMillis().toString(),
        )
    }

    fun delete(id: String) {
        redisTemplate.delete(KEY_PREFIX + id)
    }

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val id = token.getClaimAsString(SessionTokens.SESSION_ID_CLAIM) ?: return OAuth2TokenValidatorResult.success()
        val ttl = SessionTokens.sessionExpiresAt(token)?.let(::ttl) ?: return OAuth2TokenValidatorResult.failure(ENDED)
        val active =
            try {
                redisTemplate.execute(
                    TOUCH_SCRIPT,
                    listOf(KEY_PREFIX + id),
                    clock.millis().toString(),
                    IDLE_TIMEOUT.toMillis().toString(),
                    ttl.toMillis().toString(),
                ) == 1L
            } catch (e: Exception) {
                log.error("Cannot check whether a session is active, rejecting it", e)
                false
            }
        return if (active) OAuth2TokenValidatorResult.success() else OAuth2TokenValidatorResult.failure(ENDED)
    }

    /** How long the hash is kept from now on; `null` if the session is over. */
    private fun ttl(sessionExpiresAt: Instant): Duration? {
        val remaining = Duration.between(clock.instant(), sessionExpiresAt)
        return if (remaining.isNegative || remaining.isZero) null else minOf(remaining, IDLE_TIMEOUT)
    }

    companion object {
        const val KEY_PREFIX = "bits:auth:session:"

        /** A session ends after this long without a request. */
        val IDLE_TIMEOUT: Duration = Duration.ofHours(24)

        /** A session ends this long after the login at the latest, however active it is. */
        val MAX_LIFETIME: Duration = Duration.ofDays(7)

        private const val DATA_FIELD = "data"
        private const val LAST_SEEN_FIELD = "lastSeen"
        private val ENDED = OAuth2Error("invalid_token", "The session was ended", null)
        private val json = Json { ignoreUnknownKeys = true }

        private val SAVE_SCRIPT =
            RedisScript.of<Long>(
                """
                redis.call('HSET', KEYS[1], '$DATA_FIELD', ARGV[1], '$LAST_SEEN_FIELD', ARGV[2])
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
                return 1
                """.trimIndent(),
                Long::class.java,
            )

        /** Counts a request if the session has not been idle for too long, and ends it otherwise; 1 if it is active. */
        private val TOUCH_SCRIPT =
            RedisScript.of<Long>(
                """
                local lastSeen = tonumber(redis.call('HGET', KEYS[1], '$LAST_SEEN_FIELD'))
                if not lastSeen then return 0 end
                if tonumber(ARGV[1]) - lastSeen > tonumber(ARGV[2]) then
                    redis.call('DEL', KEYS[1])
                    return 0
                end
                redis.call('HSET', KEYS[1], '$LAST_SEEN_FIELD', ARGV[1])
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
                return 1
                """.trimIndent(),
                Long::class.java,
            )
    }
}

/** A session as kept in Redis; times are epoch seconds. */
@Serializable
data class StoredSession(
    val subject: String,
    val name: String,
    val avatarUrl: String? = null,
    val role: String,
    val discordRoleIds: List<String> = emptyList(),
    /** End of the session, [SessionStore.MAX_LIFETIME] after the login. */
    val expiresAt: Long,
    /** Discord's refresh token, encrypted by [SessionSecrets]; `null` for the admin account. */
    val refreshToken: String? = null,
    /** Fingerprint of the admin's password hash at login, so that a new hash ends the session; `null` for Discord users. */
    val adminFingerprint: String? = null,
    /** Expiry of the newest token issued for this session; until then, another request reuses it instead of checking again. */
    val tokenExpiresAt: Long,
) {
    val user: SessionUser
        get() = SessionUser(subject, name, avatarUrl, Role.valueOf(role), discordRoleIds)

    fun with(user: SessionUser) =
        copy(name = user.name, avatarUrl = user.avatarUrl, role = user.role.name, discordRoleIds = user.discordRoleIds)
}
