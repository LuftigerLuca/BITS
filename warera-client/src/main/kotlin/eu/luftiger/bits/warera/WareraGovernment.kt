package eu.luftiger.bits.warera

import kotlinx.serialization.Serializable

/**
 * Office holders of a country, as user IDs; a vacant presidency is `null`. The ministers come as a list, in which a
 * title may appear several times. The single fields `minOfEconomy` etc. that WarEra used before November 2026 are
 * outdated where they still appear and are not read.
 */
@Serializable
data class WareraGovernment(
    val country: String,
    val president: String? = null,
    val vicePresident: String? = null,
    val ministers: List<WareraMinister> = emptyList(),
    val congressMembers: List<String> = emptyList(),
)

/** A member of the cabinet; [title] is WarEra's name of the office, e.g. `minOfInterior` or `advisor`. */
@Serializable
data class WareraMinister(
    val user: String,
    val title: String,
)
