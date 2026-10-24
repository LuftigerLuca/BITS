package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.auth.domain.SessionUser
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

/**
 * Checks the credentials of the admin account from the environment.
 *
 * Every attempt is counted before the password is checked, in one atomic step, so parallel requests cannot slip
 * past the limit while BCrypt runs. Per IP address, [MAX_ATTEMPTS] attempts are checked in a fixed window of
 * [WINDOW] from the first one; a successful login resets the count. Across all addresses, [GLOBAL_MAX_ATTEMPTS]
 * attempts in a window, successful ones included, lock the admin login for everybody, against attacks spread over
 * many addresses. The counters live in Redis and always expire.
 */
@Service
class AdminLoginService(
    private val properties: AuthProperties,
    private val passwordEncoder: PasswordEncoder,
    private val redisTemplate: StringRedisTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun login(
        username: String,
        password: String,
        clientIp: String,
    ): AdminLoginResult {
        val admin = properties.admin
        if (!admin.enabled) {
            log.info("Admin login from {} rejected: the admin account is disabled", clientIp)
            return AdminLoginResult.Disabled
        }

        val key = ATTEMPTS_KEY_PREFIX + clientIp
        if (count(key) > MAX_ATTEMPTS) {
            log.warn("Admin login from {} rejected: locked out after {} attempts", clientIp, MAX_ATTEMPTS)
            return AdminLoginResult.LockedOut
        }
        val globalAttempts = count(GLOBAL_ATTEMPTS_KEY)
        if (globalAttempts > GLOBAL_MAX_ATTEMPTS) {
            if (globalAttempts == GLOBAL_MAX_ATTEMPTS + 1L) {
                log.warn("Admin login locked for everybody: {} attempts within {}", GLOBAL_MAX_ATTEMPTS, WINDOW)
            }
            log.warn("Admin login from {} rejected: the admin login is locked for everybody", clientIp)
            return AdminLoginResult.LockedOut
        }

        // The password is checked even for a wrong username, so the response time does not tell which one was wrong.
        val usernameMatches = MessageDigest.isEqual(username.toByteArray(), admin.username.toByteArray())
        val passwordMatches = passwordEncoder.matches(password, admin.passwordHash)
        if (usernameMatches && passwordMatches) {
            redisTemplate.delete(key)
            log.info("Admin login of {} from {}", admin.username, clientIp)
            return AdminLoginResult.Success(SessionUser.admin(admin.username))
        }

        // The username of a failed attempt is not logged, it may be the password typed into the wrong field.
        log.info("Admin login from {} failed: wrong credentials", clientIp)
        return AdminLoginResult.InvalidCredentials
    }

    /** Counts an attempt and returns the count in the current window. */
    private fun count(key: String): Long = redisTemplate.execute(COUNT_SCRIPT, listOf(key), WINDOW.toMillis().toString())

    companion object {
        const val ATTEMPTS_KEY_PREFIX = "bits:auth:admin-login-attempts:"
        const val GLOBAL_ATTEMPTS_KEY = "bits:auth:admin-login-attempts"
        const val MAX_ATTEMPTS = 5
        const val GLOBAL_MAX_ATTEMPTS = 50
        val WINDOW: Duration = Duration.ofMinutes(15)

        /** `EXPIRE ... NX` starts the window with the first attempt and gives every counter a TTL. */
        private val COUNT_SCRIPT =
            RedisScript.of(
                """
                local count = redis.call('INCR', KEYS[1])
                redis.call('PEXPIRE', KEYS[1], ARGV[1], 'NX')
                return count
                """.trimIndent(),
                Long::class.java,
            )
    }
}

sealed interface AdminLoginResult {
    data class Success(
        val user: SessionUser,
    ) : AdminLoginResult

    data object InvalidCredentials : AdminLoginResult

    data object LockedOut : AdminLoginResult

    data object Disabled : AdminLoginResult
}
