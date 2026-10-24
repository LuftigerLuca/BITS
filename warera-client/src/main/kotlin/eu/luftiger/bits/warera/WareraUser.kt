package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WareraUser(
    @SerialName("_id") val id: String,
    val username: String,
    /** Country of citizenship. */
    val country: String? = null,
    /** Military unit the user is a member of. */
    val mu: String? = null,
    val militaryRank: Int = 0,
    val leveling: Leveling = Leveling(),
) {
    @Serializable
    data class Leveling(
        val level: Int = 0,
    )
}
