package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import org.springframework.http.ResponseCookie
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Starts the sessions of the dashboard and issues their tokens: a signed JWT in an HttpOnly cookie, valid for
 * [TOKEN_VALIDITY]. The session itself is an entry in Redis ([SessionStore]) and lasts as long as it is used, up to
 * [SessionStore.MAX_LIFETIME]. The cookie is kept by the browser for that long, so that an expired token can be
 * renewed ([SessionRefresher]); the renewal checks the user's role and Discord roles again.
 *
 * A session ends before that with its logout, which deletes the entry and puts the token's ID (`jti`) on a denylist
 * ([RevokedSessions]), or when the check at the renewal fails. The last resort is rotating `BITS_JWT_SECRET`,
 * which ends every session (see deploy/README.md).
 */
@Component
class SessionTokens(
    private val jwtEncoder: JwtEncoder,
    private val clock: Clock,
    private val sessionStore: SessionStore,
    private val secrets: SessionSecrets,
    private val properties: AuthProperties,
) {
    /** Starts a session for [user]; [discordRefreshToken] is needed to renew it, unless it is the admin account. */
    fun cookie(
        user: SessionUser,
        discordRefreshToken: String? = null,
    ): ResponseCookie {
        val now = clock.instant()
        val id = UUID.randomUUID().toString()
        val expiresAt = now + SessionStore.MAX_LIFETIME
        val tokenExpiresAt = now + TOKEN_VALIDITY
        sessionStore.save(
            id,
            StoredSession(
                subject = user.subject,
                name = user.name,
                avatarUrl = user.avatarUrl,
                role = user.role.name,
                discordRoleIds = user.discordRoleIds,
                expiresAt = expiresAt.epochSecond,
                refreshToken = discordRefreshToken?.let(secrets::encrypt),
                adminFingerprint = if (user.discordUserId == null) secrets.fingerprint(properties.admin.passwordHash) else null,
                tokenExpiresAt = tokenExpiresAt.epochSecond,
            ),
        )
        return cookie(token(id, user, expiresAt, tokenExpiresAt), expiresAt)
    }

    /** A new token for an existing session, valid until [tokenExpiresAt]. */
    fun cookie(
        sessionId: String,
        session: StoredSession,
        tokenExpiresAt: Instant,
    ): ResponseCookie {
        val expiresAt = Instant.ofEpochSecond(session.expiresAt)
        return cookie(token(sessionId, session.user, expiresAt, tokenExpiresAt), expiresAt)
    }

    fun clearedCookie(): ResponseCookie = cookie("", Duration.ZERO)

    private fun token(
        sessionId: String,
        user: SessionUser,
        sessionExpiresAt: Instant,
        tokenExpiresAt: Instant,
    ): String {
        val claims =
            JwtClaimsSet
                .builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(user.subject)
                .issuedAt(clock.instant())
                .expiresAt(minOf(tokenExpiresAt, sessionExpiresAt))
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(SESSION_EXPIRES_CLAIM, sessionExpiresAt.epochSecond)
                .claim(NAME_CLAIM, user.name)
                .claim(ROLE_CLAIM, user.role.name)
                .apply { user.avatarUrl?.let { claim(AVATAR_CLAIM, it) } }
                .apply { if (user.discordRoleIds.isNotEmpty()) claim(DISCORD_ROLES_CLAIM, user.discordRoleIds) }
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    private fun cookie(
        token: String,
        sessionExpiresAt: Instant,
    ) = cookie(token, Duration.between(clock.instant(), sessionExpiresAt))

    private fun cookie(
        value: String,
        maxAge: Duration,
    ): ResponseCookie =
        ResponseCookie
            .from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAge)
            .build()

    companion object {
        const val COOKIE_NAME = "bits_session"
        const val ROLE_CLAIM = "role"
        const val SESSION_ID_CLAIM = "sid"
        private const val SESSION_EXPIRES_CLAIM = "session_exp"
        private const val ISSUER = "bits"
        private const val NAME_CLAIM = "name"
        private const val AVATAR_CLAIM = "avatar"
        private const val DISCORD_ROLES_CLAIM = "discord_roles"

        /**
         * How long a token is valid before the session is checked again: a Discord user who loses the Administrator
         * permission, the guild or the app keeps the session as before for this long at most.
         */
        val TOKEN_VALIDITY: Duration = Duration.ofHours(1)

        fun sessionExpiresAt(jwt: Jwt): Instant? =
            when (val value = jwt.claims[SESSION_EXPIRES_CLAIM]) {
                is Instant -> value
                is Number -> Instant.ofEpochSecond(value.toLong())
                else -> null
            }

        fun user(jwt: Jwt) =
            SessionUser(
                subject = requireNotNull(jwt.subject),
                name = requireNotNull(jwt.getClaimAsString(NAME_CLAIM)),
                avatarUrl = jwt.getClaimAsString(AVATAR_CLAIM),
                role = Role.valueOf(requireNotNull(jwt.getClaimAsString(ROLE_CLAIM))),
                discordRoleIds = jwt.getClaimAsStringList(DISCORD_ROLES_CLAIM).orEmpty(),
            )
    }
}
