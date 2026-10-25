package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The population fields are only shown on the dashboard, so they may be missing; the role sync needs only [id] and
 * [name] and keeps working without them.
 */
@Serializable
data class WareraCountry(
    @SerialName("_id") val id: String,
    val name: String,
    /** Not used by BITS, so it may be missing. */
    val code: String? = null,
    /** Citizens of the country. */
    val currentPopulation: Int? = null,
    val rankings: Rankings? = null,
) {
    /** Citizens who were active lately, as WarEra counts them for its ranking. */
    val activePopulation: Int?
        get() = rankings?.countryActivePopulation?.value

    @Serializable
    data class Rankings(
        val countryActivePopulation: Ranking? = null,
    )

    /** WarEra also sends the rank and tier, which BITS does not use. */
    @Serializable
    data class Ranking(
        val value: Int,
    )
}
