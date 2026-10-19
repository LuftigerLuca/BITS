package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

@Serializable
data class PingRequest(
    val discordUserId: String,
)

@Serializable
data class PingResponse(
    /** Number of pings the backend has stored so far, including this one. */
    val pingCount: Long,
    /** Number of countries reported by the WarEra API, or null if it could not be reached. */
    val wareraCountryCount: Int?,
)
