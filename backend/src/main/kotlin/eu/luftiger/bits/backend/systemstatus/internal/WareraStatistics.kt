package eu.luftiger.bits.backend.systemstatus.internal

import eu.luftiger.bits.warera.WareraCallListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Counts the calls to WarEra and the answers. Whether WarEra is reachable follows from these calls only, so the page
 * "System-Status" uses none of the limit: an answer below 500 means it is, no answer or a server error that it is not.
 */
@Component
class WareraStatistics(
    private val clock: Clock,
) : WareraCallListener {
    val requests = UsageCounters(clock)
    val callers = UsageCounters(clock)

    /** No answer, a server error or 429. */
    val failures = UsageCounters(clock)
    val rateLimited = UsageCounters(clock)
    val retries = UsageCounters(clock)
    val throttled = UsageCounters(clock)
    val cacheHits = UsageCounters(clock)
    val cacheMisses = UsageCounters(clock)
    val permitWait = LatencyHistogram()
    private val latencies = ConcurrentHashMap<String, LatencyHistogram>()

    @Volatile
    private var reachability: Reachability? = null

    fun reachability(): Reachability? = reachability

    override fun requestSent(
        procedure: String,
        status: Int?,
        permitWait: Duration,
        duration: Duration,
    ) {
        requests.add(procedure)
        callers.add(WareraCaller.current().name)
        if (status == null || status >= SERVER_ERROR || status == TOO_MANY_REQUESTS) failures.add(procedure)
        if (status == TOO_MANY_REQUESTS) rateLimited.add(procedure)
        this.permitWait.record(permitWait)
        latencies.computeIfAbsent(procedure) { LatencyHistogram() }.record(duration)
        val reachable = status != null && status < SERVER_ERROR
        val problem = if (reachable) null else status?.let { "HTTP $it" } ?: "Keine Antwort"
        recordReachability(reachable, problem, duration)
    }

    override fun retried(procedure: String) = retries.add(procedure)

    override fun throttled(procedure: String) = throttled.add(procedure)

    override fun cacheLookup(
        procedure: String,
        hit: Boolean,
    ) = (if (hit) cacheHits else cacheMisses).add(procedure)

    fun latency(procedure: String): LatencyHistogram.Latency? = latencies[procedure]?.summary()

    @Synchronized
    private fun recordReachability(
        reachable: Boolean,
        problem: String?,
        duration: Duration,
    ) {
        val now = clock.instant()
        val previous = reachability
        reachability =
            Reachability(
                reachable = reachable,
                since = if (previous?.reachable == reachable) previous.since else now,
                lastContact = now,
                lastDuration = duration,
                lastFailure = if (reachable) previous?.lastFailure else now,
                lastProblem = problem ?: previous?.lastProblem,
            )
    }

    /** As of the last request; [lastProblem] belongs to [lastFailure]. */
    data class Reachability(
        val reachable: Boolean,
        val since: Instant,
        val lastContact: Instant,
        val lastDuration: Duration,
        val lastFailure: Instant?,
        val lastProblem: String?,
    )

    private companion object {
        const val SERVER_ERROR = 500
        const val TOO_MANY_REQUESTS = 429
    }
}
