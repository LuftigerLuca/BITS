package eu.luftiger.bits.backend.systemstatus.api

import eu.luftiger.bits.backend.systemstatus.api.dto.EventStreamState
import org.springframework.modulith.NamedInterface

/**
 * Extension point for the module that hands events to the bot: how far the bot is behind. The page "System-Status"
 * shows it next to the events counted by [EventCounter].
 */
@NamedInterface("api")
interface EventStreamStatus {
    fun state(): EventStreamState
}

/** Counts the events handed to the bot, by type, for the page "System-Status". */
@NamedInterface("api")
interface EventCounter {
    fun published(type: String)
}
