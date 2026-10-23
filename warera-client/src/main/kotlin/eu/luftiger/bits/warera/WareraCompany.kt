package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WareraCompany(
    @SerialName("_id") val id: String,
    /** The only free text field of a company. */
    val name: String,
)
