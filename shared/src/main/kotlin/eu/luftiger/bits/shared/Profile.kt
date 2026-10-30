package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRequest(
    val discordUserId: String,
    /** The member who asked, who may look up others too; the backend's rate limit counts for them. */
    val requestedBy: String? = null,
)

/** Outcome of looking up the WarEra profile of a Discord account. Each subtype is identified by its `type` field. */
@Serializable
sealed interface ProfileResponse {
    /**
     * Fresh from WarEra, apart from the names of the country and the military unit and the parties and the government
     * of the country, which may be cached. Details WarEra could not deliver are listed in [unavailable]; their fields
     * then say nothing.
     */
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
        /** The party in the country of citizenship, `null` if the user is in none. */
        val party: ProfileParty? = null,
        /** German names of the offices held in the government of the country of citizenship, highest first. */
        val offices: List<String> = emptyList(),
        val companyCount: Int = 0,
        /**
         * IDs of the roles BITS manages, highest first, whether the member has them or not; the bot picks those the
         * member has.
         */
        val managedRoleIds: List<String> = emptyList(),
        val unavailable: Set<ProfileDetail> = emptySet(),
    ) : ProfileResponse

    @Serializable
    @SerialName("not_linked")
    data object NotLinked : ProfileResponse

    /** The linked WarEra account no longer exists. */
    @Serializable
    @SerialName("warera_user_not_found")
    data object WareraUserNotFound : ProfileResponse
}

@Serializable
data class ProfileParty(
    val name: String,
    val leader: Boolean,
)

/** A part of the profile that needs a WarEra call of its own and may fail while the rest is there. */
@Serializable
enum class ProfileDetail {
    PARTY,
    OFFICES,
    COMPANIES,
}
