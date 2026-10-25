package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/**
 * All members of the guild except bots, sent once all of them are loaded; replaces whatever the bot reported before.
 * The backend deletes the links of everybody who is missing, so a list that is not complete must never be sent.
 */
@Serializable
data class DiscordMembersReport(
    val discordUserIds: List<String>,
    /**
     * The same members with their names and roles, for the preview of a rule change in the dashboard. Empty from a bot
     * that does not send it yet; the backend then has no preview.
     */
    val members: List<ReportedMember> = emptyList(),
)

@Serializable
data class ReportedMember(
    val discordUserId: String,
    /** As Discord shows it. */
    val name: String,
    /** Without the `@everyone` role. */
    val roleIds: List<String>,
)

/** A member who joined or left the guild. */
@Serializable
data class DiscordMemberChange(
    val discordUserId: String,
    /** As Discord shows it, for the system log. */
    val memberName: String? = null,
)
