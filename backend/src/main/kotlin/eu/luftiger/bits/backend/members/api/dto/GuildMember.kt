package eu.luftiger.bits.backend.members.api.dto

import org.springframework.modulith.NamedInterface

/** A member of the guild with the roles they hold, as the bot reported them last. */
@NamedInterface("api")
data class GuildMember(
    val discordUserId: String,
    /** As Discord shows it; `null` for a member who joined after the last list. */
    val name: String?,
    val roleIds: Set<String>,
)
