package eu.luftiger.bits.shared

import java.time.Duration

/** Redis stream the backend publishes [BitsEvent]s to. */
object EventStream {
    const val KEY = "bits:events"

    /** Field of a stream entry that holds the JSON-encoded event. */
    const val PAYLOAD_FIELD = "payload"

    /**
     * How long the backend keeps entries in the stream. A bot that is down for longer misses events, which the next
     * full role sync makes up for. A week of hourly full syncs is a few megabytes even for a large guild.
     */
    val RETENTION: Duration = Duration.ofDays(7)

    /** Consumer group of the bot. */
    const val BOT_GROUP = "bot"

    /** Events the bot gave up on after several failed deliveries, with the original stream ID in `originalId`. */
    const val DEAD_LETTER_KEY = "bits:events:dead"
}
