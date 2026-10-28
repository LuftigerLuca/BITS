package eu.luftiger.bits.warera

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A military unit; a user is a member of at most one. */
@Serializable
data class WareraMilitaryUnit(
    @SerialName("_id") val id: String,
    val name: String,
    /** User ID of the owner, `null` if WarEra names none. */
    @SerialName("user") val owner: String? = null,
    val roles: WareraMilitaryUnitRoles = WareraMilitaryUnitRoles(),
)

/** Who leads a [WareraMilitaryUnit] besides its owner, as user IDs. The owner may be listed as a manager, too. */
@Serializable
data class WareraMilitaryUnitRoles(
    val managers: List<String> = emptyList(),
    val commanders: List<String> = emptyList(),
)
