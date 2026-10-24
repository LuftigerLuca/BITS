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

/**
 * Issues the session of the dashboard: a signed JWT in an HttpOnly cookie, valid for [VALIDITY] without refresh.
 * Nothing is stored on the server, so a session cannot be revoked before it expires.
 */
@Component
class SessionTokens(
    private val jwtEncoder: JwtEncoder,
    private val clock: Clock,
) {
    fun cookie(user: SessionUser): ResponseCookie {
        val now = clock.instant()
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(ISSUER)
                .subject(user.subject)
                .issuedAt(now)
                .expiresAt(now + VALIDITY)
                .claim(NAME_CLAIM, user.name)
                .claim(ROLE_CLAIM, user.role.name)
                .apply { user.avatarUrl?.let { claim(AVATAR_CLAIM, it) } }
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return cookie(token, VALIDITY)
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

        val VALIDITY: Duration = Duration.ofHours(8)

        fun user(jwt: Jwt) =
            SessionUser(
                subject = requireNotNull(jwt.subject),
                name = requireNotNull(jwt.getClaimAsString(NAME_CLAIM)),
                avatarUrl = jwt.getClaimAsString(AVATAR_CLAIM),
                role = Role.valueOf(requireNotNull(jwt.getClaimAsString(ROLE_CLAIM))),
            )
    }
}
