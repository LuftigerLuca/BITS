package eu.luftiger.bits.backend.auth.internal

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * Ends a successful Discord login with the session cookie and a redirect to the dashboard. The HTTP session only
 * held the OAuth2 state during the redirect to Discord and back, so it is dropped here.
 */
@Component
class DiscordLoginSuccessHandler(
    private val sessionTokens: SessionTokens,
    private val properties: AuthProperties,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as DiscordLoginService.DiscordPrincipal
        request.getSession(false)?.invalidate()
        response.addHeader(HttpHeaders.SET_COOKIE, sessionTokens.cookie(principal.user).toString())
        response.sendRedirect("${properties.publicUrl}/")
    }
}

/** Sends the user back to the login page, which shows a message for the error code in `error`. */
@Component
class DiscordLoginFailureHandler(
    private val properties: AuthProperties,
) : AuthenticationFailureHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        request.getSession(false)?.invalidate()
        val errorCode = (exception as? OAuth2AuthenticationException)?.error?.errorCode
        val error = errorCode?.takeIf { it in FORWARDED_ERRORS } ?: GENERIC_ERROR
        // The forwarded errors were logged where they occurred; the rest come from Spring Security, e.g. a stale state.
        if (error == GENERIC_ERROR) log.warn("Discord login failed: {} ({})", errorCode, exception.message)
        response.sendRedirect("${properties.publicUrl}/login?error=$error")
    }

    private companion object {
        val FORWARDED_ERRORS = setOf(DiscordLoginService.NOT_MEMBER_ERROR, DiscordLoginService.DISCORD_UNAVAILABLE_ERROR)
        const val GENERIC_ERROR = "discord"
    }
}
