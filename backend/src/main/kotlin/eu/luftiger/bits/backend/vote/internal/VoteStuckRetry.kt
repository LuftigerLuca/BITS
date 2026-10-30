package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.VoteClosedEvent
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant

@Service
class VoteStuckRetry(
    private val voteRepository: VoteRepository,
    private val eventPublisher: EventPublisher,
    private val evaluator: VoteEvaluator,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /**
     * Gets results out that are stuck: evaluations that failed are tried again after
     * [VoteProperties.retryInterval], and the bot is asked again for the role members or to post the result after
     * [VoteProperties.botTimeout], in case it lost the event.
     */
    fun retryStuck(properties: VoteProperties) {
        val now = clock.instant()
        voteRepository.findByStatusOrderByStartedAtDesc(VoteStatus.COUNTING).forEach { vote ->
            val lastAttempt = vote.lastAttemptAt ?: Instant.MIN
            if (vote.electorateReportedAt != null) {
                if (!lastAttempt.isAfter(now - properties.retryInterval)) evaluator.evaluateSoon(requireNotNull(vote.id))
            } else if (!lastAttempt.isAfter(now - properties.botTimeout)) {
                republish(requireNotNull(vote.id)) { VoteClosedEvent(it, clock.millis()) }
            }
        }
        voteRepository
            .findByStatusOrderByStartedAtDesc(VoteStatus.EVALUATED)
            .filter { it.resultPostedAt == null && !(it.lastAttemptAt ?: Instant.MIN).isAfter(now - properties.botTimeout) }
            .forEach { vote -> republish(requireNotNull(vote.id)) { VoteEvaluatedEvent(it, clock.millis()) } }
    }

    private fun republish(
        voteId: Long,
        event: (Long) -> BitsEvent,
    ) {
        transactionTemplate.executeWithoutResult {
            val vote = voteRepository.findForUpdate(voteId) ?: return@executeWithoutResult
            vote.lastAttemptAt = clock.instant()
            eventPublisher.publish(event(voteId))
        }
    }
}
