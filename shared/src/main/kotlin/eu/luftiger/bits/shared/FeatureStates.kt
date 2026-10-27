package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/**
 * Which features with Discord commands an admin left on. Every field defaults to on, so a feature the other side does
 * not know yet stays usable.
 */
@Serializable
data class FeatureStates(
    val verification: Boolean = true,
    val roleSync: Boolean = true,
    val nicknames: Boolean = true,
    val profile: Boolean = true,
    val votes: Boolean = true,
)
