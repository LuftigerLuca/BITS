package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.auth.domain.SessionUser
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Renews the token of a session once it has expired, after checking the user again:
 *
 * - A Discord user is looked up with a new access token from the refresh token kept in the session. Their role and
 *   Discord roles are taken over as they are now, so a former admin goes on as USER. Who has left the guild or
 *   deauthorized the app loses the session.
 * - If Discord cannot be reached, the session goes on with the role of the last check, and the check is tried again
 *   after [RETRY_DELAY]; the session's own end still applies.
 * - The admin account is not checked anywhere, but a new password hash or a disabled account ends its sessions.
 *
 * Requests of a session that arrive together with the same expired token are handled one after the other; the first
 * checks, the others get a token like the one it was issued. Discord rotates the refresh token on every use, so two
 * checks at once would make the second one fail. One backend runs (ADR 0005), so a lock in memory is enough.
 */
@Component
class SessionRefresher(
    private val sessionStore: SessionStore,
    private val sessionTokens: SessionTokens,
    private val secrets: SessionSecrets,
    private val discordApi: DiscordApi,
    private val discordLoginService: DiscordLoginService,
    private val properties: AuthProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val locks = Array(LOCK_STRIPES) { ReentrantLock() }

    /** The new session cookie, or `null` if the session has ended. Throws if Redis cannot be reached. */
    fun refresh(sessionId: String): ResponseCookie? =
        locks[Math.floorMod(sessionId.hashCode(), LOCK_STRIPES)].withLock {
            val session = sessionStore.find(sessionId) ?: return null
            val now = clock.instant()
            val expiresAt = Instant.ofEpochSecond(session.expiresAt)
            if (!now.isBefore(expiresAt)) {
                sessionStore.delete(sessionId)
                return null
            }
            val issued = Instant.ofEpochSecond(session.tokenExpiresAt)
            if (now.isBefore(issued)) return sessionTokens.cookie(sessionId, session, issued)

            val renewed =
                when (val result = check(session)) {
                    is Check.Valid ->
                        session
                            .with(result.user)
                            .copy(
                                refreshToken = result.refreshToken?.let(secrets::encrypt) ?: session.refreshToken,
                                tokenExpiresAt = minOf(now + SessionTokens.TOKEN_VALIDITY, expiresAt).epochSecond,
                            )
                    is Check.Unavailable ->
                        session.copy(
                            refreshToken = result.refreshToken?.let(secrets::encrypt) ?: session.refreshToken,
                            tokenExpiresAt = minOf(now + RETRY_DELAY, expiresAt).epochSecond,
                        )
                    Check.Ended -> {
                        sessionStore.delete(sessionId)
                        return null
                    }
                }
            sessionStore.save(sessionId, renewed)
            sessionTokens.cookie(sessionId, renewed, Instant.ofEpochSecond(renewed.tokenExpiresAt))
        }

    private fun check(session: StoredSession): Check {
        val user = session.user
        if (user.discordUserId == null) return checkAdmin(session)
        val refreshToken =
            session.refreshToken?.let(secrets::decrypt) ?: run {
                log.info("Session of {} ended: no readable refresh token", user.discordUserId)
                return Check.Ended
            }
        // Once Discord has issued new tokens, the old refresh token is spent, so the new one is kept in any case.
        var newRefreshToken: String? = null
        return try {
            val tokens = discordApi.refreshTokens(refreshToken)
            newRefreshToken = tokens.refreshToken
            val current = discordLoginService.resolve(tokens.accessToken)
            if (current == null) {
                log.info("Session of {} ended: no longer a member of the guild", user.discordUserId)
                Check.Ended
            } else {
                if (current.role != user.role) log.info("Session of {} goes on as {}", user.discordUserId, current.role)
                Check.Valid(current, newRefreshToken)
            }
        } catch (e: DiscordGrantRevokedException) {
            log.info("Session of {} ended: {}", user.discordUserId, e.message)
            Check.Ended
        } catch (e: RestClientException) {
            log.warn("Session of {} not checked, Discord not reachable ({}); retrying in {}", user.discordUserId, e.message, RETRY_DELAY)
            Check.Unavailable(newRefreshToken)
        }
    }

    private fun checkAdmin(session: StoredSession): Check {
        val admin = properties.admin
        val valid =
            admin.enabled &&
                session.subject == SessionUser.admin(admin.username).subject &&
                session.adminFingerprint == secrets.fingerprint(admin.passwordHash)
        if (!valid) log.info("Session of {} ended: the admin account was changed or disabled", session.subject)
        return if (valid) Check.Valid(session.user, refreshToken = null) else Check.Ended
    }

    private sealed interface Check {
        data class Valid(
            val user: SessionUser,
            val refreshToken: String?,
        ) : Check

        data class Unavailable(
            val refreshToken: String?,
        ) : Check

        data object Ended : Check
    }

    companion object {
        /** How long a session goes on unchecked while Discord cannot be reached, before the next attempt. */
        val RETRY_DELAY: Duration = Duration.ofMinutes(1)

        private const val LOCK_STRIPES = 64
    }
}
