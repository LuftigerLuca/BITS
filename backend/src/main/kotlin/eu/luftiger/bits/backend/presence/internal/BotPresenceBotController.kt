package eu.luftiger.bits.backend.presence.internal

import eu.luftiger.bits.shared.BotPresence
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** The bot asks on start, so a change it missed while it was down still applies. */
@RestController
class BotPresenceBotController(
    private val presenceService: BotPresenceService,
) {
    @GetMapping("/api/bot-presence")
    fun presence(): BotPresence = presenceService.presence()
}
