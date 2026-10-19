package eu.luftiger.bits.backend.shared

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/** Rejects requests that do not carry the service token of the bot. */
class ServiceTokenFilter(
    serviceToken: String,
) : OncePerRequestFilter() {
    private val expected = "Bearer $serviceToken".toByteArray()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val actual = request.getHeader(HttpHeaders.AUTHORIZATION)?.toByteArray()
        if (actual == null || !MessageDigest.isEqual(expected, actual)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            return
        }
        filterChain.doFilter(request, response)
    }
}
