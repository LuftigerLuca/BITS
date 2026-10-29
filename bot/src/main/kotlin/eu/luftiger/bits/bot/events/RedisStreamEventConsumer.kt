package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.UndeliveredEventReport
import eu.luftiger.bits.shared.decodeEvent
import io.lettuce.core.Consumer
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.RedisBusyException
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.StreamMessage
import io.lettuce.core.XAddArgs
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XPendingArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Reads events from the Redis stream [EventStream.KEY] as member of the consumer group of [lane] and acknowledges each
 * one with `XACK` once [handler] has processed it. Events of the other lanes are acknowledged without handling them.
 *
 * An event that fails stays pending and is tried again once it has been idle for [Settings.retryAfter], also after
 * a restart. After [Settings.maxDeliveries] deliveries it is moved to [EventStream.DEAD_LETTER_KEY] and acknowledged,
 * so a broken event never blocks the ones after it. Unreadable entries, e.g. of an event type this bot does not know
 * yet, take the same way: a newer bot can still pick them up before they run out of deliveries.
 *
 * Every event given up on goes to [onDeadLetter] as well, for the backend's system log.
 *
 * The consumer survives Redis going away: it reconnects with backoff and creates the group again if it is lost.
 * It creates the groups of all lanes before it reads, [EventLane.MAIN] first: a lane added by an update starts where
 * that group is, so it gets the events the bot had not handled yet before the update, see [createGroups].
 */
class RedisStreamEventConsumer(
    private val redisClient: RedisClient,
    private val handler: EventHandler,
    private val settings: Settings = Settings(),
    private val lane: EventLane = EventLane.MAIN,
    consumerName: String = "bot",
    private val onDeadLetter: (UndeliveredEventReport) -> Unit = {},
) {
    data class Settings(
        /** How long a read waits for new events. Also bounds how long [stop] takes. */
        val blockTimeout: Duration = Duration.ofSeconds(2),
        /** How long a failed event rests before it is tried again. */
        val retryAfter: Duration = Duration.ofSeconds(30),
        /** Deliveries before an event goes to the dead letter stream; with [retryAfter], about two minutes. */
        val maxDeliveries: Long = 5,
        val minReconnectDelay: Duration = Duration.ofSeconds(1),
        val maxReconnectDelay: Duration = Duration.ofSeconds(30),
    )

    private val log = LoggerFactory.getLogger(javaClass)
    private val consumer = Consumer.from(lane.group, consumerName)

    @Volatile
    private var running = true

    /** Lets [run] return once the event at hand is done, instead of cancelling it halfway. */
    fun stop() {
        running = false
    }

    suspend fun run() {
        var reconnectDelay = settings.minReconnectDelay
        // Only the first group starts at the end of the stream; a lost group misses nothing published since.
        var groupStart = "$"
        while (running && currentCoroutineContext().isActive) {
            try {
                redisClient.connect().use { connection ->
                    // Fail fast when Redis goes away, instead of after Lettuce's default of a minute.
                    connection.timeout = settings.blockTimeout + COMMAND_TIMEOUT_MARGIN
                    val commands = connection.sync()
                    createGroups(commands, groupStart)
                    groupStart = "0"
                    reconnectDelay = settings.minReconnectDelay
                    consume(commands)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!running) break
                if (e.isNoGroup()) {
                    log.warn("Consumer group {} is gone, creating it again", lane.group)
                } else {
                    log.error("Reading events failed, reconnecting in {}", reconnectDelay, e)
                    delay(reconnectDelay.toMillis())
                    reconnectDelay = minOf(reconnectDelay.multipliedBy(2), settings.maxReconnectDelay)
                }
            }
        }
    }

    private suspend fun consume(commands: RedisCommands<String, String>) {
        while (running && currentCoroutineContext().isActive) {
            retryPending(commands)
            commands
                .xreadgroup(
                    consumer,
                    XReadArgs.Builder.block(settings.blockTimeout).count(BATCH_SIZE),
                    XReadArgs.StreamOffset.lastConsumed(EventStream.KEY),
                ).forEach { handle(commands, it) }
        }
    }

    /** Claims the events that failed long enough ago, or moves them to the dead letter stream once they are used up. */
    private suspend fun retryPending(commands: RedisCommands<String, String>) {
        val due =
            commands.xpending(
                EventStream.KEY,
                XPendingArgs.Builder
                    .xpending(lane.group, Range.unbounded(), Limit.from(BATCH_SIZE))
                    .idle(settings.retryAfter),
            )
        for (pending in due) {
            if (!running) return
            // Empty if it was handled in the meantime or trimmed from the stream.
            val message = commands.xclaim(EventStream.KEY, consumer, settings.retryAfter.toMillis(), pending.id).firstOrNull() ?: continue
            if (pending.redeliveryCount >= settings.maxDeliveries) {
                deadLetter(commands, message, pending.redeliveryCount)
            } else {
                handle(commands, message)
            }
        }
    }

    private suspend fun handle(
        commands: RedisCommands<String, String>,
        message: StreamMessage<String, String>,
    ) {
        val payload = message.body[EventStream.PAYLOAD_FIELD]
        if (!lane.handles(eventType(payload)) || process(message.id, payload)) {
            commands.xack(EventStream.KEY, lane.group, message.id)
        }
    }

    /**
     * Handles one stream entry. Returns whether it is done and can be acknowledged. Logs only what identifies the
     * entry: payloads hold the Discord IDs and roles of whole member lists.
     */
    internal suspend fun process(
        id: String,
        payload: String?,
    ): Boolean {
        val event =
            try {
                decodeEvent(requireNotNull(payload) { "Stream entry has no ${EventStream.PAYLOAD_FIELD} field" })
            } catch (e: IllegalArgumentException) {
                // Covers SerializationException as well. The message names the problem, not the data.
                log.error("Event {} is unreadable ({}), it stays pending: {}", id, preview(payload), e.message?.lineSequence()?.first())
                return false
            }
        return try {
            handler.handle(event)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Handling {} failed, it stays pending", describe(id, event), e)
            false
        }
    }

    private fun deadLetter(
        commands: RedisCommands<String, String>,
        message: StreamMessage<String, String>,
        deliveries: Long,
    ) {
        commands.xadd(
            EventStream.DEAD_LETTER_KEY,
            XAddArgs.Builder.maxlen(DEAD_LETTER_MAX_LENGTH).approximateTrimming(),
            message.body + mapOf(ORIGINAL_ID_FIELD to message.id),
        )
        commands.xack(EventStream.KEY, lane.group, message.id)
        log.error("Giving up on event {} after {} deliveries, moved it to {}", message.id, deliveries, EventStream.DEAD_LETTER_KEY)
        onDeadLetter(UndeliveredEventReport(message.id, eventType(message.body[EventStream.PAYLOAD_FIELD]), deliveries))
    }

    /** Only the `type` of the event, the rest may hold member data. `null` if the payload is no JSON object. */
    private fun eventType(payload: String?): String? =
        try {
            payload?.let {
                Json
                    .parseToJsonElement(it)
                    .jsonObject["type"]
                    ?.jsonPrimitive
                    ?.content
            }
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * Creates the group of [EventLane.MAIN] at [start] if it is missing, then those of the other lanes where the main
     * group is: before its oldest pending event, or else after the last one it read. The consumers of all lanes do
     * this before they read, so the main group cannot move on past a role sync before the role sync lane exists.
     */
    private fun createGroups(
        commands: RedisCommands<String, String>,
        start: String,
    ) {
        createGroup(commands, EventLane.MAIN.group, start)
        val existing = groups(commands)
        for (other in EventLane.entries - EventLane.MAIN) {
            if (other.group !in existing) createGroup(commands, other.group, positionOfMain(commands, existing) ?: start)
        }
    }

    /** The groups of the stream, by name, with the ID of the last entry each read. */
    private fun groups(commands: RedisCommands<String, String>): Map<String, String> =
        commands.xinfoGroups(EventStream.KEY).associate { group ->
            val fields = (group as List<*>).chunked(2).associate { (key, value) -> key.toString() to value.toString() }
            fields.getValue("name") to fields.getValue("last-delivered-id")
        }

    /** The ID a new group starts after to get what [EventLane.MAIN] has not handled yet; `null` without that group. */
    private fun positionOfMain(
        commands: RedisCommands<String, String>,
        groups: Map<String, String>,
    ): String? {
        val lastDelivered = groups[EventLane.MAIN.group] ?: return null
        val pending = commands.xpending(EventStream.KEY, EventLane.MAIN.group)
        return if (pending.count > 0) idBefore(pending.messageIds.lower.value) else lastDelivered
    }

    private fun createGroup(
        commands: RedisCommands<String, String>,
        group: String,
        start: String,
    ) {
        try {
            commands.xgroupCreate(
                XReadArgs.StreamOffset.from(EventStream.KEY, start),
                group,
                XGroupCreateArgs.Builder.mkstream(),
            )
            if (group != EventLane.MAIN.group) log.info("Created consumer group {} after entry {}", group, start)
        } catch (_: RedisBusyException) {
            // The group exists already.
        }
    }

    private fun Exception.isNoGroup() = this is RedisCommandExecutionException && message.orEmpty().startsWith("NOGROUP")

    private fun describe(
        id: String,
        event: BitsEvent,
    ) = "${event::class.simpleName} $id (occurred at ${event.occurredAt})"

    private fun preview(payload: String?) =
        if (payload == null) "no payload" else "${payload.length} chars, starting with ${payload.take(PREVIEW_LENGTH)}"

    internal companion object {
        private val COMMAND_TIMEOUT_MARGIN: Duration = Duration.ofSeconds(3)
        private const val BATCH_SIZE = 10L
        private const val PREVIEW_LENGTH = 40
        private const val DEAD_LETTER_MAX_LENGTH = 1_000L
        private const val ORIGINAL_ID_FIELD = "originalId"

        /** The largest stream ID below [id]; a group created there reads [id] first. */
        internal fun idBefore(id: String): String {
            val (millis, sequence) = id.split('-').map { it.toULong() }
            return if (sequence > 0u) "$millis-${sequence - 1u}" else "${millis - 1u}-${ULong.MAX_VALUE}"
        }
    }
}
