package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WareraCountry(
    @SerialName("_id") val id: String,
    val name: String,
    val code: String,
)
