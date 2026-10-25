package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/**
 * What the bot did with a [RoleSyncEvent]; the backend keeps it in its system log. Sent once per applied sync, with
 * the names as Discord shows them right now, so the log stays readable after a member or role is gone.
 */
@Serializable
data class RoleSyncReport(
    /** Members the bot brought to the target state. */
    val memberCount: Int,
    /** Members left alone because a newer sync had set their roles already. */
    val staleMemberCount: Int,
    val changes: List<ReportedRoleChange>,
    /** Nicknames the bot changed or could not change. */
    val nicknameChanges: List<ReportedNicknameChange> = emptyList(),
)

@Serializable
data class ReportedRoleChange(
    val discordUserId: String,
    val memberName: String,
    val roleId: String,
    /** `null` if the role is gone from Discord. */
    val roleName: String?,
    val outcome: RoleChangeOutcome,
    /** Why a change was skipped or failed, in German for the dashboard. */
    val reason: String? = null,
)

@Serializable
enum class RoleChangeOutcome {
    GRANTED,
    REMOVED,

    /** Not tried, e.g. because the role is above the bot or the sync would take it from too many members. */
    SKIPPED,

    /** Tried, but Discord refused it. */
    FAILED,
}

@Serializable
data class ReportedNicknameChange(
    val discordUserId: String,
    /** As Discord showed the member before the change. */
    val memberName: String,
    /** The new nickname; `null` if it was cleared. */
    val nickname: String?,
    val outcome: NicknameChangeOutcome,
    /** Why a change was skipped or failed, in German for the dashboard. */
    val reason: String? = null,
)

@Serializable
enum class NicknameChangeOutcome {
    SET,

    /** Cleared, since the member lost their link. */
    RESET,

    /** Not tried, e.g. because the member is above the bot or the bot may not manage nicknames. */
    SKIPPED,

    /** Tried, but Discord refused it. */
    FAILED,
}

/** An event the bot gave up on and moved to [EventStream.DEAD_LETTER_KEY]. Carries no payload, which holds member data. */
@Serializable
data class UndeliveredEventReport(
    /** ID of the entry in [EventStream.KEY]. */
    val streamId: String,
    /** The event's `type`, or `null` if the entry could not be read at all. */
    val eventType: String?,
    val deliveries: Long,
)
