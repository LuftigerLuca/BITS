package eu.luftiger.bits.warera

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The role sync judges these fields, so a field WarEra stops sending must not read as "no country" or "level 0", which
 * would take roles away from everybody. The fields WarEra always sends are [Required] in the answer; without them, the
 * lookup fails and the member keeps their roles. The defaults only serve code that builds a user itself.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WareraUser(
    @SerialName("_id") val id: String,
    val username: String,
    /** Country of citizenship; WarEra sends it for every user. */
    @Required val country: String? = null,
    /** Military unit the user is a member of. WarEra leaves the field out for users without one. */
    val mu: String? = null,
    @Required val militaryRank: Int = 0,
    @Required val leveling: Leveling = Leveling(),
) {
    @Serializable
    data class Leveling(
        @Required val level: Int = 0,
    )
}
