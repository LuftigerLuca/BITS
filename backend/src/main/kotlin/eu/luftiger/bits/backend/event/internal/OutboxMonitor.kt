package eu.luftiger.bits.backend.event.internal

import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration

/**
 * Records in the [SystemLog] when events for the bot have waited longer than [STUCK_AFTER] to be written to Redis.
 * [RedisStreamWriter] keeps trying on its own; this only makes the outage visible. Recorded once per outage, not on
 * every check; after a restart, an outage that is still going on is recorded again.
 */
@Component
class OutboxMonitor(
    private val jdbc: JdbcTemplate,
    private val systemLog: SystemLog,
    private val clock: Clock,
) {
    private var reported = false

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    fun check() {
        val stuck =
            jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM event_publication
                WHERE completion_date IS NULL AND publication_date < ? AND listener_id LIKE ?
                """,
                Int::class.java,
                Timestamp.from(clock.instant() - STUCK_AFTER),
                "%${RedisStreamWriter::class.simpleName}%",
            ) ?: 0
        if (stuck > 0 && !reported) systemLog.recordProblem(SystemProblem.OutboxStuck(stuck))
        reported = stuck > 0
    }

    companion object {
        val STUCK_AFTER: Duration = Duration.ofMinutes(10)
    }
}
