package eu.luftiger.bits.backend.auth.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI

/**
 * Rejects requests that change state and come from a page of another origin, with 403.
 *
 * `SameSite=Strict` keeps the session cookie away from other sites, but not from other origins of the same site,
 * such as a neighbouring subdomain. Browsers name the origin of a request in `Sec-Fetch-Site` and `Origin`; one of
 * them must point to [publicUrl]. Requests without either header do not come from a browser page, so they carry no
 * cookie the user did not mean to send.
 */
class SameOriginFilter(
    publicUrl: String,
) : OncePerRequestFilter() {
    private val origin = origin(publicUrl)

    override fun shouldNotFilter(request: HttpServletRequest) = request.method in SAFE_METHODS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val fetchSite = request.getHeader(SEC_FETCH_SITE)
        val requestOrigin = request.getHeader(HttpHeaders.ORIGIN)
        val allowed =
            when {
                fetchSite != null -> fetchSite == "same-origin" && (requestOrigin == null || origin(requestOrigin) == origin)
                requestOrigin != null -> origin(requestOrigin) == origin
                else -> true
            }
        if (allowed) {
            filterChain.doFilter(request, response)
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
        }
    }

    private companion object {
        const val SEC_FETCH_SITE = "Sec-Fetch-Site"
        val SAFE_METHODS = setOf(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name())

        /** Scheme, host and port; `null` for `Origin: null` and anything else that is not a URL. */
        fun origin(url: String): String? =
            runCatching {
                val uri = URI(url)
                val port = if (uri.port == -1) "" else ":${uri.port}"
                "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port"
            }.getOrNull()
    }
}
