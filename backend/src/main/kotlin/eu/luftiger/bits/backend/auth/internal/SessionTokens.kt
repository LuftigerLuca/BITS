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
import java.util.UUID

/**
 * Issues the session of the dashboard: a signed JWT in an HttpOnly cookie, valid for [validity] without refresh.
 *
 * Two things end a session before it expires, since the token itself cannot be changed once issued:
 * - **Logout** puts the token's ID (`jti`) on a denylist in Redis ([RevokedSessions]) until it would have expired,
 *   so a copied cookie stops working too.
 * - **Lost admin rights** are not seen by the token, which keeps the role from the login. ADMIN sessions therefore
 *   last only [ADMIN_VALIDITY]; the role is decided anew at the next login. The same goes for the member's Discord
 *   roles, which a USER session keeps for up to [USER_VALIDITY]. Checking Discord on every request would
 *   need the user's access token, which BITS does not keep.
 *
 * The last resort is rotating `BITS_JWT_SECRET`, which ends every session (see deploy/README.md).
 */
@Component
class SessionTokens(
    private val jwtEncoder: JwtEncoder,
    private val clock: Clock,
) {
    fun cookie(user: SessionUser): ResponseCookie {
        val now = clock.instant()
        val validity = validity(user.role)
        val claims =
            JwtClaimsSet
                .builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(user.subject)
                .issuedAt(now)
                .expiresAt(now + validity)
                .claim(NAME_CLAIM, user.name)
                .claim(ROLE_CLAIM, user.role.name)
                .apply { user.avatarUrl?.let { claim(AVATAR_CLAIM, it) } }
                .apply { if (user.discordRoleIds.isNotEmpty()) claim(DISCORD_ROLES_CLAIM, user.discordRoleIds) }
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return cookie(token, validity)
    }

    fun clearedCookie(): ResponseCookie = cookie("", Duration.ZERO)

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
        private const val ISSUER = "bits"
        private const val NAME_CLAIM = "name"
        private const val AVATAR_CLAIM = "avatar"

        /** Missing in sessions from before the roles were kept, which end within [USER_VALIDITY]. */
        private const val DISCORD_ROLES_CLAIM = "discord_roles"

        val USER_VALIDITY: Duration = Duration.ofHours(8)

        /** How long a Discord user keeps ADMIN after losing the Administrator permission, at most. */
        val ADMIN_VALIDITY: Duration = Duration.ofHours(1)

        fun validity(role: Role): Duration =
            when (role) {
                Role.USER -> USER_VALIDITY
                Role.ADMIN -> ADMIN_VALIDITY
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
