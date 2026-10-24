package eu.luftiger.bits.backend.event.internal

import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Hands events that could not be written to Redis to their listeners again. Spring Modulith does this on its own
 * only when the backend starts.
 */
@Component
class OutboxResubmitter(
    private val incompletePublications: IncompleteEventPublications,
) {
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    fun resubmit() = incompletePublications.resubmitIncompletePublicationsOlderThan(RETRY_AFTER)

    private companion object {
        /** Long enough that a write that is still under way is not started a second time. */
        val RETRY_AFTER: Duration = Duration.ofMinutes(1)
    }
}
