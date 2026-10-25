package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/** Whether BITS should set the member's nickname to their WarEra name. */
@Serializable
data class NicknamePreferenceRequest(
    val discordUserId: String,
    val enabled: Boolean,
)

@Serializable
data class NicknamePreferenceResponse(
    val enabled: Boolean,
    /** Name of the linked WarEra account, which becomes the nickname; `null` if the member is not linked. */
    val wareraUsername: String?,
)
