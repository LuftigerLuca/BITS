package eu.luftiger.bits.backend.event.internal

import eu.luftiger.bits.shared.EventStream
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Appends events to the Redis stream [EventStream.KEY], from which the bot reads them via its consumer group, and
 * drops the entries older than [EventStream.RETENTION]. A failed write leaves the publication incomplete, so
 * [OutboxResubmitter] tries again.
 */
@Component
class RedisStreamWriter(
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock,
) {
    @ApplicationModuleListener
    fun write(event: BotEvent) {
        val record = StreamRecords.string(mapOf(EventStream.PAYLOAD_FIELD to event.payload)).withStreamKey(EventStream.KEY)
        // Stream IDs start with the time in milliseconds, so this is the oldest ID to keep.
        val oldest = RecordId.of(clock.millis() - EventStream.RETENTION.toMillis(), 0)
        redisTemplate.opsForStream<String, String>().add(record, XAddOptions.trim(TrimOptions.minId(oldest).exact()))
    }
}
