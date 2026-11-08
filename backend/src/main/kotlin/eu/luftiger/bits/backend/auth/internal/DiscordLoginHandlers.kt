package eu.luftiger.bits.backend.auth.internal

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * Ends a successful Discord login with the session cookie and a redirect to the dashboard. The HTTP session only
 * held the OAuth2 state during the redirect to Discord and back, so it is dropped here. Discord's refresh token is
 * handed over by [DiscordTokenHandover] in a request attribute.
 */
@Component
class DiscordLoginSuccessHandler(
    private val sessionTokens: SessionTokens,
    private val properties: AuthProperties,
) : AuthenticationSuccessHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val principal = authentication.principal as DiscordLoginService.DiscordPrincipal
        request.getSession(false)?.invalidate()
        val refreshToken = request.getAttribute(DISCORD_REFRESH_TOKEN_ATTRIBUTE) as String?
        if (refreshToken ==
            null
        ) {
            log.warn("Discord login of {} without a refresh token, the session cannot be renewed", principal.user.discordUserId)
        }
        response.addHeader(HttpHeaders.SET_COOKIE, sessionTokens.cookie(principal.user, refreshToken).toString())
        response.sendRedirect("${properties.publicUrl}/")
    }
}

/** Request attribute that carries Discord's refresh token from [DiscordTokenHandover] to [DiscordLoginSuccessHandler]. */
const val DISCORD_REFRESH_TOKEN_ATTRIBUTE = "eu.luftiger.bits.discordRefreshToken"

/**
 * Takes the place of Spring Security's store for the tokens of a login, which runs right before the success handler.
 * Nothing is stored here; only the refresh token is passed on to the session, the access token is dropped.
 */
object DiscordTokenHandover : OAuth2AuthorizedClientRepository {
    override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(
        clientRegistrationId: String,
        principal: Authentication,
        request: HttpServletRequest,
    ): T? = null

    override fun saveAuthorizedClient(
        authorizedClient: OAuth2AuthorizedClient,
        principal: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        authorizedClient.refreshToken?.let { request.setAttribute(DISCORD_REFRESH_TOKEN_ATTRIBUTE, it.tokenValue) }
    }

    override fun removeAuthorizedClient(
        clientRegistrationId: String,
        principal: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = Unit
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
        const val GENERIC_ERROR = DiscordLoginService.GENERIC_ERROR
    }
}
