package eu.luftiger.bits.backend.events

import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.encode
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** Appends events to the Redis stream [EventStream.KEY], from which the bot reads them via its consumer group. */
@Component
class RedisStreamEventPublisher(
    private val redisTemplate: StringRedisTemplate,
) : EventPublisher {
    override fun publish(event: BitsEvent) {
        val record =
            StreamRecords
                .string(mapOf(EventStream.PAYLOAD_FIELD to event.encode()))
                .withStreamKey(EventStream.KEY)
        redisTemplate.opsForStream<String, String>().add(record)
    }
}
