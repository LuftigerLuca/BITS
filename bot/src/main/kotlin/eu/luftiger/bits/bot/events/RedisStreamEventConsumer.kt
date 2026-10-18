package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.decodeEvent
import io.lettuce.core.Consumer
import io.lettuce.core.RedisBusyException
import io.lettuce.core.RedisClient
import io.lettuce.core.StreamMessage
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Reads events from the Redis stream [EventStream.KEY] as member of the consumer group [EventStream.BOT_GROUP] and
 * acknowledges each one with `XACK` once [handler] has processed it.
 *
 * On start, messages that were delivered before but never acknowledged (e.g. because the bot crashed) are read
 * again first.
 */
class RedisStreamEventConsumer(
    private val redisClient: RedisClient,
    private val handler: EventHandler,
    private val consumerName: String = "bot",
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val consumer = Consumer.from(EventStream.BOT_GROUP, consumerName)

    suspend fun run() {
        redisClient.connect().use { connection ->
            val commands = connection.sync()
            createGroup(commands)
            var offset = "0"
            while (currentCoroutineContext().isActive) {
                try {
                    val messages = read(commands, offset)
                    // An empty read of the pending messages means all of them are handled; continue with new ones.
                    if (offset == "0" && messages.isEmpty()) offset = ">"
                    messages.forEach { message ->
                        if (process(message.body[EventStream.PAYLOAD_FIELD])) {
                            commands.xack(EventStream.KEY, EventStream.BOT_GROUP, message.id)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Reading events failed, retrying", e)
                    delay(RETRY_DELAY)
                }
            }
        }
    }

    /** Handles one stream entry. Returns whether it is done and can be acknowledged. */
    internal suspend fun process(payload: String?): Boolean {
        val event =
            try {
                decodeEvent(requireNotNull(payload) { "Stream entry has no ${EventStream.PAYLOAD_FIELD} field" })
            } catch (e: IllegalArgumentException) {
                // Covers SerializationException as well. A broken entry never becomes valid, so drop it.
                log.error("Dropping unreadable event: {}", payload, e)
                return true
            }
        return try {
            handler.handle(event)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Handling {} failed, it stays pending", event, e)
            false
        }
    }

    private fun read(
        commands: RedisCommands<String, String>,
        offset: String,
    ): List<StreamMessage<String, String>> =
        commands.xreadgroup(
            consumer,
            XReadArgs.Builder.block(BLOCK_TIMEOUT).count(BATCH_SIZE),
            XReadArgs.StreamOffset.from(EventStream.KEY, offset),
        )

    private fun createGroup(commands: RedisCommands<String, String>) {
        try {
            commands.xgroupCreate(
                XReadArgs.StreamOffset.from(EventStream.KEY, "$"),
                EventStream.BOT_GROUP,
                XGroupCreateArgs.Builder.mkstream(),
            )
        } catch (_: RedisBusyException) {
            // The group exists already.
        }
    }

    private companion object {
        val BLOCK_TIMEOUT: Duration = Duration.ofSeconds(5)
        const val BATCH_SIZE = 10L
        const val RETRY_DELAY = 5_000L
    }
}
