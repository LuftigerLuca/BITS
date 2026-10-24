package eu.luftiger.bits.backend.event.api

import eu.luftiger.bits.shared.BitsEvent
import org.springframework.modulith.NamedInterface

/**
 * Cross-module boundary of the event module: hands events to the bot.
 *
 * Within a transaction, the event is stored with it and only reaches the bot if the transaction commits. Outside of
 * one, it gets a transaction of its own. Either way it is written to Redis afterwards and again until that works, so
 * an outage of Redis delays events but neither loses them nor fails the caller.
 */
@NamedInterface("api")
interface EventPublisher {
    fun publish(event: BitsEvent)
}
