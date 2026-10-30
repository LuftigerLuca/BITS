package eu.luftiger.bits.backend.event.internal

import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.systemstatus.api.EventCounter
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.encode
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Stores events in Spring Modulith's event publication registry (table `event_publication`) as part of the
 * transaction, from where [RedisStreamWriter] takes them once it has committed.
 */
@Component
class OutboxEventPublisher(
    private val applicationEvents: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val counter: EventCounter,
) : EventPublisher {
    // Joins the caller's transaction, if there is one.
    override fun publish(event: BitsEvent) {
        transactionTemplate.executeWithoutResult { applicationEvents.publishEvent(BotEvent(event.encode())) }
        // Counted once stored, even if a surrounding transaction rolls back later; for the statistics, that is close enough.
        counter.published(event::class.simpleName.orEmpty())
    }
}

/** An event for the bot, already encoded, so that the registry stores exactly what goes to Redis. */
data class BotEvent(
    val payload: String,
)
