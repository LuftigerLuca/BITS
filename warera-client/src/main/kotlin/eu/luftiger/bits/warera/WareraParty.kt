package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A political party of a country; a user is a member of at most one. */
@Serializable
data class WareraParty(
    @SerialName("_id") val id: String,
    val name: String,
    /** User IDs of all members, the leader included. */
    val members: List<String> = emptyList(),
)
