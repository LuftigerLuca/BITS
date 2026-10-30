package eu.luftiger.bits.backend.vote.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Every minute: ends the votes that are due, and gets stuck results out, see [VoteStuckRetry.retryStuck]. */
@Component
class VoteJobs(
    private val voteService: VoteService,
    private val stuckRetry: VoteStuckRetry,
    private val properties: VoteProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${bits.vote.cron}")
    fun run() {
        try {
            voteService.closeDue()
            stuckRetry.retryStuck(properties)
        } catch (e: Exception) {
            log.error("Vote job failed, trying again in a minute", e)
        }
    }
}
