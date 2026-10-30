package eu.luftiger.bits.backend.systemstatus.internal

import java.time.Clock
import java.time.Duration

/**
 * Counts by key in memory: in total since the backend started and per minute for the last [HISTORY]. One backend runs
 * at a time (ADR 0005), so nothing has to be shared; a restart starts from zero.
 */
class UsageCounters(
    private val clock: Clock,
) {
    private val counters = HashMap<String, Counter>()

    @Synchronized
    fun add(
        key: String,
        amount: Long = 1,
    ) {
        if (amount <= 0) return
        counters.getOrPut(key, ::Counter).add(currentMinute(), amount)
    }

    /** Every key with its counts, in no particular order. */
    @Synchronized
    fun snapshot(): List<KeyCount> {
        val minute = currentMinute()
        return counters.map { (key, counter) -> KeyCount(key, counter.total, counter.sumSince(minute - LAST_HOUR_MINUTES + 1, minute)) }
    }

    /** All keys added up per [BUCKET] of the last [HISTORY], oldest first; the last bucket is the current one. */
    @Synchronized
    fun history(): List<Long> {
        val minute = currentMinute()
        // Buckets end with the current minute, so the newest one is never empty only because it just started.
        return (BUCKETS - 1 downTo 0).map { bucket ->
            val last = minute - bucket * BUCKET_MINUTES
            counters.values.sumOf { it.sumSince(last - BUCKET_MINUTES + 1, last) }
        }
    }

    @Synchronized
    fun total(key: String): Long = counters[key]?.total ?: 0

    /** Over all keys, in the last [minutes] minutes, the current one included. */
    @Synchronized
    fun recent(minutes: Long = LAST_HOUR_MINUTES): Long {
        val minute = currentMinute()
        return counters.values.sumOf { it.sumSince(minute - minutes + 1, minute) }
    }

    /** Over all keys, the most in one minute of the last [HISTORY]. */
    @Synchronized
    fun peakPerMinute(): Long {
        val minute = currentMinute()
        return (0 until HISTORY_MINUTES).maxOfOrNull { ago -> counters.values.sumOf { it.sumSince(minute - ago, minute - ago) } } ?: 0
    }

    private fun currentMinute() = clock.millis() / MINUTE_MILLIS

    /** [lastHour] is the sum of the last 60 minutes, the current one included. */
    data class KeyCount(
        val key: String,
        val total: Long,
        val lastHour: Long,
    )

    private class Counter {
        var total = 0L
            private set

        /** Slot `minute % size`; [minutes] says which minute a slot holds, so stale slots count as empty. */
        private val counts = LongArray(HISTORY_MINUTES)
        private val minutes = LongArray(HISTORY_MINUTES) { -1 }

        fun add(
            minute: Long,
            amount: Long,
        ) {
            total += amount
            val slot = (minute % HISTORY_MINUTES).toInt()
            if (minutes[slot] != minute) {
                minutes[slot] = minute
                counts[slot] = 0
            }
            counts[slot] += amount
        }

        fun sumSince(
            from: Long,
            to: Long,
        ): Long {
            var sum = 0L
            for (minute in maxOf(from, to - HISTORY_MINUTES + 1)..to) {
                val slot = (minute % HISTORY_MINUTES).toInt()
                if (minutes[slot] == minute) sum += counts[slot]
            }
            return sum
        }
    }

    companion object {
        val HISTORY: Duration = Duration.ofHours(24)
        val BUCKET: Duration = Duration.ofMinutes(15)
        private const val MINUTE_MILLIS = 60_000L
        private val HISTORY_MINUTES = HISTORY.toMinutes().toInt()
        private val BUCKET_MINUTES = BUCKET.toMinutes()
        private val BUCKETS = (HISTORY_MINUTES / BUCKET_MINUTES).toInt()
        const val LAST_HOUR_MINUTES = 60L
    }
}

/**
 * Durations sorted into fixed buckets since the backend started, enough for rough percentiles: a percentile is the
 * upper bound of the bucket it falls into.
 */
class LatencyHistogram {
    private val counts = LongArray(BOUNDS_MILLIS.size + 1)
    private var max = 0L

    @Synchronized
    fun record(duration: Duration) {
        val millis = duration.toMillis()
        val bucket = BOUNDS_MILLIS.indexOfFirst { millis <= it }.let { if (it < 0) BOUNDS_MILLIS.size else it }
        counts[bucket]++
        max = maxOf(max, millis)
    }

    /** `null` until something was recorded. */
    @Synchronized
    fun summary(): Latency? {
        val total = counts.sum()
        if (total == 0L) return null
        return Latency(percentile(total, 0.5), percentile(total, 0.95), max)
    }

    private fun percentile(
        total: Long,
        quantile: Double,
    ): Long {
        val rank = Math.ceil(total * quantile).toLong()
        var seen = 0L
        counts.forEachIndexed { bucket, count ->
            seen += count
            if (seen >= rank) return minOf(BOUNDS_MILLIS.getOrElse(bucket) { max }, max)
        }
        return max
    }

    data class Latency(
        val p50Millis: Long,
        val p95Millis: Long,
        val maxMillis: Long,
    )

    private companion object {
        val BOUNDS_MILLIS = longArrayOf(5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 30_000)
    }
}
