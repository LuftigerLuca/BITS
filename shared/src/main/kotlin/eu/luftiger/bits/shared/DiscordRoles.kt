package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/** All roles of the guild as the bot sees them; replaces whatever the bot reported before. */
@Serializable
data class DiscordRolesReport(
    val roles: List<DiscordRoleDto>,
)

@Serializable
data class DiscordRoleDto(
    val id: String,
    val name: String,
    /** RGB value, `null` for the default color. */
    val color: Int?,
    /** Position in the role list; higher roles are listed first. */
    val position: Int,
    /** Whether the bot may give and take the role: below its own highest role, not `@everyone` and not an integration role. */
    val assignable: Boolean,
)
