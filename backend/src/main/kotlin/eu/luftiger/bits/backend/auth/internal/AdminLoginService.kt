package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.auth.domain.SessionUser
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

/**
 * Checks the credentials of the admin account from the environment. After [MAX_FAILURES] failed attempts, an IP
 * address is locked out for [LOCK_DURATION]; the counter lives in Redis and expires on its own.
 */
@Service
class AdminLoginService(
    private val properties: AuthProperties,
    private val passwordEncoder: PasswordEncoder,
    private val redisTemplate: StringRedisTemplate,
) {
    fun login(
        username: String,
        password: String,
        clientIp: String,
    ): AdminLoginResult {
        val admin = properties.admin
        if (!admin.enabled) return AdminLoginResult.Disabled

        val key = FAILURES_KEY_PREFIX + clientIp
        val failures = redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
        if (failures >= MAX_FAILURES) return AdminLoginResult.LockedOut

        // The password is checked even for a wrong username, so the response time does not tell which one was wrong.
        val usernameMatches = MessageDigest.isEqual(username.toByteArray(), admin.username.toByteArray())
        val passwordMatches = passwordEncoder.matches(password, admin.passwordHash)
        if (usernameMatches && passwordMatches) {
            redisTemplate.delete(key)
            return AdminLoginResult.Success(SessionUser.admin(admin.username))
        }

        redisTemplate.opsForValue().increment(key)
        redisTemplate.expire(key, LOCK_DURATION)
        return AdminLoginResult.InvalidCredentials
    }

    companion object {
        const val FAILURES_KEY_PREFIX = "bits:auth:admin-login-failures:"
        const val MAX_FAILURES = 5
        val LOCK_DURATION: Duration = Duration.ofMinutes(15)
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
