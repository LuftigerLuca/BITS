package eu.luftiger.bits.backend.systemstatus.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Attributes the WarEra calls of a request to what the request is for, see [WareraCaller.ofPath], and counts the
 * requests of the bot by endpoint. Runs before Spring Security, so requests it rejects count too; those have no
 * endpoint and are counted as [UNMATCHED].
 */
@Component
@Order(BEFORE_SECURITY)
class RequestStatisticsFilter(
    clock: Clock,
) : OncePerRequestFilter() {
    val requests = UsageCounters(clock)
    val clientErrors = UsageCounters(clock)
    val serverErrors = UsageCounters(clock)
    private val latencies = ConcurrentHashMap<String, LatencyHistogram>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI.removePrefix(request.contextPath)
        val caller = WareraCaller.ofPath(path)
        if (caller == null) {
            filterChain.doFilter(request, response)
            return
        }
        val start = System.nanoTime()
        try {
            WareraCaller.within(caller) { filterChain.doFilter(request, response) }
        } finally {
            if (path.startsWith("/api/")) record(request, response.status, Duration.ofNanos(System.nanoTime() - start))
        }
    }

    fun latency(endpoint: String): LatencyHistogram.Latency? = latencies[endpoint]?.summary()

    private fun record(
        request: HttpServletRequest,
        status: Int,
        duration: Duration,
    ) {
        // The pattern, not the path, so that /api/votes/1 and /api/votes/2 count as one endpoint.
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: UNMATCHED
        val endpoint = "${request.method} $pattern"
        requests.add(endpoint)
        if (status in CLIENT_ERRORS) clientErrors.add(endpoint)
        if (status >= SERVER_ERROR) serverErrors.add(endpoint)
        latencies.computeIfAbsent(endpoint) { LatencyHistogram() }.record(duration)
    }

    companion object {
        const val UNMATCHED = "*"
        private val CLIENT_ERRORS = 400..499
        private const val SERVER_ERROR = 500
    }
}

/** Spring Security's filter has the order -100. */
private const val BEFORE_SECURITY = -101
