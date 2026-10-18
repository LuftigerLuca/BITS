package eu.luftiger.bits.backend.events

import eu.luftiger.bits.shared.BitsEvent

/** Hands events to the bot. */
interface EventPublisher {
    fun publish(event: BitsEvent)
}
