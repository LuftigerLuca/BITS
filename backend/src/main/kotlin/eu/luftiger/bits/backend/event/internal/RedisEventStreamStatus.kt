package eu.luftiger.bits.backend.event.internal

import eu.luftiger.bits.backend.systemstatus.api.EventStreamStatus
import eu.luftiger.bits.backend.systemstatus.api.dto.ConsumerGroupState
import eu.luftiger.bits.backend.systemstatus.api.dto.EventStreamState
import eu.luftiger.bits.shared.EventStream
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp

/** The outbox in Postgres and the stream in Redis, each read on its own, so one being down leaves the other. */
@Component
class RedisEventStreamStatus(
    private val jdbc: JdbcTemplate,
    private val redisTemplate: StringRedisTemplate,
) : EventStreamStatus {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun state(): EventStreamState {
        val outbox = read { outbox() }
        return EventStreamState(
            outboxPending = outbox?.first,
            outboxOldest = outbox?.second?.toInstant(),
            streamLength = read { redisTemplate.opsForStream<String, String>().size(EventStream.KEY) },
            deadLetters = read { redisTemplate.opsForStream<String, String>().size(EventStream.DEAD_LETTER_KEY) },
            groups = read { groups() },
        )
    }

    private fun outbox(): Pair<Long, Timestamp?> =
        jdbc.queryForObject(
            """
            SELECT COUNT(*), MIN(publication_date) FROM event_publication
            WHERE completion_date IS NULL AND listener_id LIKE ?
            """,
            { rs, _ -> rs.getLong(1) to rs.getTimestamp(2) },
            "%${RedisStreamWriter::class.simpleName}%",
        )!!

    /** The lag is not among the fields Spring Data names, but in the raw answer; a stream not created yet has no groups. */
    private fun groups(): List<ConsumerGroupState> {
        if (redisTemplate.hasKey(EventStream.KEY) != true) return emptyList()
        return redisTemplate.opsForStream<String, String>().groups(EventStream.KEY).toList().map { group ->
            ConsumerGroupState(
                name = group.groupName(),
                consumers = group.consumerCount() ?: 0,
                pending = group.pendingCount() ?: 0,
                lag = lag(group.raw["lag"]),
            )
        }
    }

    /** `null` while Redis cannot tell, e.g. after entries were trimmed. */
    private fun lag(value: Any?): Long? =
        when (value) {
            is Number -> value.toLong()
            is ByteArray -> value.decodeToString().toLongOrNull()
            is String -> value.toLongOrNull()
            else -> null
        }

    private fun <T> read(query: () -> T): T? =
        try {
            query()
        } catch (e: Exception) {
            log.debug("Could not read the state of the event stream", e)
            null
        }
}
