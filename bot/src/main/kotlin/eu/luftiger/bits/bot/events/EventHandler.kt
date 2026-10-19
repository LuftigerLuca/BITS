package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.BitsEvent

/** Reacts to an event from the backend. Must be idempotent: an event can be delivered more than once. */
fun interface EventHandler {
    suspend fun handle(event: BitsEvent)
}
