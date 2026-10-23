package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.shared.StartVerificationResponse

/** Access to the internal REST API of the backend. */
interface BackendClient {
    suspend fun ping(discordUserId: String): PingResponse

    /** @throws WareraUnavailableException if the backend cannot reach the WarEra API */
    suspend fun startVerification(
        discordUserId: String,
        wareraUsername: String,
    ): StartVerificationResponse

    /** @throws WareraUnavailableException if the backend cannot reach the WarEra API */
    suspend fun checkVerification(
        discordUserId: String,
        code: String,
    ): CheckVerificationResponse
}

class WareraUnavailableException(
    cause: Throwable,
) : RuntimeException("The backend cannot reach the WarEra API", cause)
