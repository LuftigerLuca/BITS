package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.BotHeartbeat

/** The part of the backend's REST API for the page "System-Status"; [KtorBackendClient] implements it. */
interface HeartbeatBackend {
    suspend fun heartbeat(heartbeat: BotHeartbeat)
}
