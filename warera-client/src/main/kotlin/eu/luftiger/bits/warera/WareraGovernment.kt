package eu.luftiger.bits.warera

import kotlinx.serialization.Serializable

/** Office holders of a country, as user IDs; vacant offices are `null`. */
@Serializable
data class WareraGovernment(
    val country: String,
    val president: String? = null,
    val vicePresident: String? = null,
    val minOfEconomy: String? = null,
    val minOfDefense: String? = null,
    val minOfForeignAffairs: String? = null,
    val congressMembers: List<String> = emptyList(),
)
