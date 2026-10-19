package eu.luftiger.bits.backend.event.api

import eu.luftiger.bits.shared.BitsEvent
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the event module: hands events to the bot. */
@NamedInterface("api")
interface EventPublisher {
    fun publish(event: BitsEvent)
}
