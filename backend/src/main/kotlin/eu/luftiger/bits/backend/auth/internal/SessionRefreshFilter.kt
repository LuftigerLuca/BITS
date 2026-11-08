package eu.luftiger.bits.backend.auth.internal

import com.nimbusds.jwt.JWTParser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock

/**
 * Renews an expired session token on the way to `/web/`, before the token is checked: the request goes on with the
 * new token and the response sets it as the cookie, so the dashboard does not notice. If the session has ended, the
 * cookie is cleared and the request goes on with the expired token, which is then rejected with 401.
 *
 * Not a bean, so that it runs only in the filter chain of `/web/` and not for every request.
 */
class SessionRefreshFilter(
    private val sessionTokenReader: SessionTokenReader,
    private val sessionRefresher: SessionRefresher,
    private val sessionTokens: SessionTokens,
    private val clock: Clock,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val expired = sessionCookie(request)?.takeIf { isExpired(it) }
        val sessionId = expired?.let(sessionTokenReader::readIgnoringExpiry)?.getClaimAsString(SessionTokens.SESSION_ID_CLAIM)
        if (sessionId != null) {
            val cookie =
                try {
                    sessionRefresher.refresh(sessionId)
                } catch (e: Exception) {
                    log.error("Cannot renew a session, rejecting it", e)
                    null
                }
            if (cookie != null) {
                request.setAttribute(RENEWED_TOKEN_ATTRIBUTE, cookie.value)
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
            } else {
                response.addHeader(HttpHeaders.SET_COOKIE, sessionTokens.clearedCookie().toString())
            }
        }
        filterChain.doFilter(request, response)
    }

    /** Read without checking the signature, which [SessionTokenReader] does before anything is renewed. */
    private fun isExpired(token: String): Boolean {
        val expiresAt = runCatching { JWTParser.parse(token).jwtClaimsSet.expirationTime }.getOrNull() ?: return false
        return expiresAt.toInstant().isBefore(clock.instant())
    }

    companion object {
        /** The renewed token, which takes the place of the cookie for the rest of the request. */
        const val RENEWED_TOKEN_ATTRIBUTE = "eu.luftiger.bits.renewedSessionToken"

        fun sessionCookie(request: HttpServletRequest): String? =
            (request.getAttribute(RENEWED_TOKEN_ATTRIBUTE) as String?)
                ?: request.cookies
                    ?.firstOrNull { it.name == SessionTokens.COOKIE_NAME }
                    ?.value
                    ?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Reads a session token whose signature is valid, of a session that has not ended, even after the token has expired:
 * to renew it, or to end it at logout.
 */
@Component
class SessionTokenReader(
    properties: AuthProperties,
    revokedSessions: RevokedSessions,
    sessionStore: SessionStore,
) {
    private val decoder =
        NimbusJwtDecoder
            .withSecretKey(properties.signingKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply { setJwtValidator(DelegatingOAuth2TokenValidator(revokedSessions, sessionStore)) }

    fun readIgnoringExpiry(token: String): Jwt? = runCatching { decoder.decode(token) }.getOrNull()
}
