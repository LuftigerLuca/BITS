package eu.luftiger.bits.backend.externalapi.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Lets a request to the third-party API through only with the bearer token of a valid API token, and makes it the
 * request's [ApiTokenAuthentication]. Without one it answers 401 with a problem detail; which endpoints the token may
 * call is up to [ExternalApiInterceptor].
 */
class ApiTokenFilter(
    private val tokens: ApiTokenService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        val token =
            header
                ?.takeIf { it.startsWith(BEARER, ignoreCase = true) }
                ?.substring(BEARER.length)
                ?.trim()
                ?.let(tokens::authenticate)
        if (token == null) {
            unauthorized(response, if (header == null) "Bearer token missing" else "Bearer token invalid or expired")
            return
        }
        val strategy = SecurityContextHolder.getContextHolderStrategy()
        strategy.context = strategy.createEmptyContext().apply { authentication = ApiTokenAuthentication(token) }
        filterChain.doFilter(request, response)
    }

    private fun unauthorized(
        response: HttpServletResponse,
        detail: String,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        // Fixed texts only, so nothing from the request ends up in the JSON.
        response.writer.write("""{"type":"about:blank","title":"Unauthorized","status":401,"detail":"$detail"}""")
    }

    private companion object {
        const val BEARER = "Bearer "
    }
}

/** A request to the third-party API made with [token]; its authorities are `SCOPE_<id>` of its scopes. */
class ApiTokenAuthentication(
    val token: AuthenticatedToken,
) : AbstractAuthenticationToken(token.scopes.map { SimpleGrantedAuthority("SCOPE_${it.id}") }) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = token.id
}
