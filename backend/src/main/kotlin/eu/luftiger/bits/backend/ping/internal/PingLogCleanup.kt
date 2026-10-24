package eu.luftiger.bits.backend.ping.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/** Pings are stored with the Discord ID of the user, so they are kept for [RETENTION] only. */
@Component
class PingLogCleanup(
    private val repository: PingLogRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${bits.cleanup.cron}")
    @Transactional
    fun deleteOld() {
        val deleted = repository.deleteCreatedBefore(clock.instant() - RETENTION)
        if (deleted > 0) log.info("Deleted {} pings older than {}", deleted, RETENTION)
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
