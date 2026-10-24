package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WareraUser(
    @SerialName("_id") val id: String,
    val username: String,
)
