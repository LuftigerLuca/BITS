package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.systemstatus.internal.LatencyHistogram
import eu.luftiger.bits.backend.systemstatus.internal.UsageCounters
import eu.luftiger.bits.backend.systemstatus.internal.WareraCaller
import eu.luftiger.bits.backend.systemstatus.internal.WareraStatistics
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageStatisticsTest {
    private val clock = MutableClock(Instant.parse("2026-01-01T12:00:00Z"))

    @Test
    fun `counts stay in the total, but leave the last hour and the history after 24 hours`() {
        val counters = UsageCounters(clock)
        counters.add("a", 2)
        clock.advance(Duration.ofMinutes(30))
        counters.add("a")
        counters.add("b")

        assertEquals(setOf(UsageCounters.KeyCount("a", 3, 3), UsageCounters.KeyCount("b", 1, 1)), counters.snapshot().toSet())
        assertEquals(2, counters.recent(1))
        assertEquals(2, counters.peakPerMinute())
        assertEquals(96, counters.history().size)
        assertEquals(listOf(2L, 0L, 2L), counters.history().takeLast(3))

        clock.advance(Duration.ofMinutes(31))
        assertEquals(2, counters.recent())

        clock.advance(Duration.ofHours(24))
        assertEquals(setOf(UsageCounters.KeyCount("a", 3, 0), UsageCounters.KeyCount("b", 1, 0)), counters.snapshot().toSet())
        assertEquals(0, counters.history().sum())
        assertEquals(0, counters.peakPerMinute())
    }

    @Test
    fun `percentiles are the upper bound of their bucket, never above the maximum`() {
        val histogram = LatencyHistogram()
        assertNull(histogram.summary())

        repeat(19) { histogram.record(Duration.ofMillis(40)) }
        histogram.record(Duration.ofMillis(700))

        assertEquals(LatencyHistogram.Latency(p50Millis = 50, p95Millis = 50, maxMillis = 700), histogram.summary())
        histogram.record(Duration.ofMillis(700))
        // In the bucket up to a second, but nothing took that long.
        assertEquals(700, histogram.summary()!!.p95Millis)
    }

    @Test
    fun `WarEra calls count for what they were made for, and decide whether WarEra is reachable`() {
        val statistics = WareraStatistics(clock)

        WareraCaller.within(WareraCaller.ROLE_SYNC) {
            statistics.requestSent("user.getUserLite", 200, Duration.ZERO, Duration.ofMillis(20))
            statistics.requestSent("user.getUserLite", 429, Duration.ZERO, Duration.ofMillis(5))
        }
        statistics.requestSent("country.getAllCountries", 200, Duration.ZERO, Duration.ofMillis(20))

        assertEquals(
            setOf("ROLE_SYNC" to 2L, "OTHER" to 1L),
            statistics.callers
                .snapshot()
                .map { it.key to it.total }
                .toSet(),
        )
        assertEquals(1, statistics.rateLimited.total("user.getUserLite"))
        assertEquals(1, statistics.failures.total("user.getUserLite"))
        // A 429 is an answer, so WarEra is reachable.
        assertEquals(true, statistics.reachability()?.reachable)

        clock.advance(Duration.ofMinutes(1))
        statistics.requestSent("user.getUserLite", null, Duration.ZERO, Duration.ofSeconds(10))

        val down = statistics.reachability()!!
        assertEquals(false, down.reachable)
        assertEquals(clock.instant(), down.since)
        assertEquals("Keine Antwort", down.lastProblem)

        clock.advance(Duration.ofMinutes(1))
        statistics.requestSent("user.getUserLite", 404, Duration.ZERO, Duration.ofMillis(20))
        val up = statistics.reachability()!!
        assertEquals(true, up.reachable)
        assertEquals(clock.instant(), up.since)
        assertEquals(down.since, up.lastFailure)
    }
}
