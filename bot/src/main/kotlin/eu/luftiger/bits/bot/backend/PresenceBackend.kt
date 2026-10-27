package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.BotPresence

/** The part of the backend's REST API for the bot's own presence; [KtorBackendClient] implements it. */
interface PresenceBackend {
    suspend fun botPresence(): BotPresence
}
