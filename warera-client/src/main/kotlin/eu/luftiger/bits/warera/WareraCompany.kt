package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WareraCompany(
    @SerialName("_id") val id: String,
    /** The only free text field of a company. */
    val name: String,
    /** WarEra user who owns the company. A company has exactly one owner; workers are only counted. */
    @SerialName("user") val ownerId: String,
)
