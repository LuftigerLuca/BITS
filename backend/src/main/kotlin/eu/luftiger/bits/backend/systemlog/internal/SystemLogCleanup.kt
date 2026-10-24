package eu.luftiger.bits.backend.systemlog.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/** The system log names members and their WarEra accounts, so its entries are kept for [RETENTION] only. */
@Component
class SystemLogCleanup(
    private val service: SystemLogService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${bits.system-log.cleanup-cron}")
    fun deleteOld() {
        val deleted = service.deleteOlderThan(RETENTION)
        if (deleted > 0) log.info("Deleted {} system log entries older than {}", deleted, RETENTION)
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
