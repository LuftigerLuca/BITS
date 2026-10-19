package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.PingResponse

/** Access to the internal REST API of the backend. */
interface BackendClient {
    suspend fun ping(discordUserId: String): PingResponse
}
