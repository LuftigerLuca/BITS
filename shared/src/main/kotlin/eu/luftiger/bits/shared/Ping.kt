package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

@Serializable
data class PingRequest(
    val discordUserId: String,
)

@Serializable
data class PingResponse(
    /** Number of pings the backend has stored, including this one; pings are kept for 30 days. */
    val pingCount: Long,
    /** Number of countries reported by the WarEra API, or null if it could not be reached. */
    val wareraCountryCount: Int?,
)
