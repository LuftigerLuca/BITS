package eu.luftiger.bits.shared

/** Redis stream the backend publishes [BitsEvent]s to. */
object EventStream {
    const val KEY = "bits:events"

    /** Field of a stream entry that holds the JSON-encoded event. */
    const val PAYLOAD_FIELD = "payload"

    /** Consumer group of the bot. */
    const val BOT_GROUP = "bot"

    /** Events the bot gave up on after several failed deliveries, with the original stream ID in `originalId`. */
    const val DEAD_LETTER_KEY = "bits:events:dead"
}
