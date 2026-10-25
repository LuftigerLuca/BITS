package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRequest(
    val discordUserId: String,
)

/** Outcome of looking up the WarEra profile of a Discord account. Each subtype is identified by its `type` field. */
@Serializable
sealed interface ProfileResponse {
    /** Fresh from WarEra, apart from the names of the country and the military unit, which may be cached. */
    @Serializable
    @SerialName("found")
    data class Found(
        val wareraUserId: String,
        /** Username as WarEra spells it now, which may differ from the one at the time of linking. */
        val wareraUsername: String,
        val level: Int,
        val militaryRank: Int,
        /** `null` if WarEra sends no country or one BITS does not know. */
        val countryName: String?,
        /** `null` if the user is in no military unit or it no longer exists. */
        val militaryUnitName: String?,
        val profileUrl: String,
    ) : ProfileResponse

    @Serializable
    @SerialName("not_linked")
    data object NotLinked : ProfileResponse

    /** The linked WarEra account no longer exists. */
    @Serializable
    @SerialName("warera_user_not_found")
    data object WareraUserNotFound : ProfileResponse
}
