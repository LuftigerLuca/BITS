package eu.luftiger.bits.backend.account.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/**
 * Deletes verifications nobody finished, which otherwise keep a WarEra account next to a Discord ID for good. A code
 * stays for [GRACE] after it expired, so that a late click on "Prüfen" still hears that it expired.
 */
@Component
class VerificationCleanup(
    private val verificationRepository: AccountVerificationRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${bits.cleanup.cron}")
    @Transactional
    fun deleteExpired() {
        val deleted = verificationRepository.deleteExpiredBefore(clock.instant() - GRACE)
        if (deleted > 0) log.info("Deleted {} expired verifications", deleted)
    }

    private companion object {
        val GRACE: Duration = Duration.ofHours(1)
    }
}
