package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Target state of the embassy threads, always for all of them. The bot brings each thread in [threads] to exactly its
 * members plus the members of [hostRoleIds], except for members who may manage threads in its channel, and gives the
 * ambassador role to [ambassadors] only. Threads that are not listed are left alone.
 */
@Serializable
@SerialName("embassy_sync")
data class EmbassySyncEvent(
    /** Run in the backend's system log; the bot reports under this ID, see [EmbassySyncReport]. */
    val syncId: Long,
    val threads: List<EmbassyThreadTarget>,
    /** Roles whose members belong in every thread, without getting the ambassador role. */
    val hostRoleIds: List<String>,
    /** `null` while no ambassador role is set; the bot then gives and takes no role. */
    val ambassadorRoleId: String?,
    /** Who gets the ambassador role, with the reason for Discord's audit log; everybody else loses it. */
    val ambassadors: Map<String, String>,
    override val occurredAt: Long,
) : BitsEvent

@Serializable
data class EmbassyThreadTarget(
    val threadId: String,
    /** Channel the thread is in, for finding it while it is archived. */
    val parentChannelId: String,
    val countryName: String,
    /** Members by Discord user ID, each with why they belong in the thread, in German. */
    val members: Map<String, String>,
    /**
     * `false` if the government of the country could not be loaded: the bot then removes nobody from the thread and
     * takes the ambassador role from none of its members.
     */
    val complete: Boolean = true,
)

/** All private threads of the guild as the bot sees them; replaces whatever the bot reported before. */
@Serializable
data class DiscordThreadsReport(
    val threads: List<DiscordThreadDto>,
)

@Serializable
data class DiscordThreadDto(
    val id: String,
    val name: String,
    val parentChannelId: String,
    val parentChannelName: String,
    val archived: Boolean,
    /** Whether the bot may add and remove members, i.e. manage threads and send messages in threads there. */
    val manageable: Boolean,
    /**
     * Whether @everyone may see the parent channel. Discord adds only members who may see it to a private thread. The
     * default stands for a bot that does not tell, so the backend checks nothing then.
     */
    val everyoneCanView: Boolean = true,
    /** Roles that may see the parent channel; empty if [everyoneCanView]. */
    val viewerRoleIds: List<String> = emptyList(),
)

/** What the bot did with an [EmbassySyncEvent], with the names as Discord shows them right now. */
@Serializable
data class EmbassySyncReport(
    val threads: List<ReportedEmbassyThread>,
    /** Thread changes carry the thread, role changes the role. */
    val changes: List<ReportedEmbassyChange>,
)

@Serializable
data class ReportedEmbassyThread(
    val threadId: String,
    /** `null` if the thread is gone. */
    val threadName: String?,
    val status: EmbassyThreadStatus,
    /** Who is in the thread after the sync; empty unless [status] is [EmbassyThreadStatus.SYNCED]. */
    val members: List<ReportedThreadMember>,
)

@Serializable
enum class EmbassyThreadStatus {
    SYNCED,

    /** Deleted, or the bot cannot see it. */
    MISSING,

    /** The bot may not manage threads or send messages in threads in its channel. */
    NO_PERMISSION,

    /** Discord refused to list its members or to unarchive it. */
    FAILED,
}

@Serializable
data class ReportedThreadMember(
    val discordUserId: String,
    val name: String,
    /** Why the member is in the thread, in German. */
    val reason: String,
)

@Serializable
data class ReportedEmbassyChange(
    val discordUserId: String,
    val memberName: String,
    val threadId: String? = null,
    val threadName: String? = null,
    val roleId: String? = null,
    /** `null` if the role is gone from Discord. */
    val roleName: String? = null,
    /** [RoleChangeOutcome.GRANTED] for a member added to a thread too. */
    val outcome: RoleChangeOutcome,
    /** Why the member was added or removed, or why it was skipped or failed, in German for the dashboard. */
    val reason: String? = null,
)

/**
 * An admin asked for a new embassy thread: the bot creates a private thread [name] in [channelId] that only moderators
 * can invite to and that archives after the longest time, and reports it under [creationId], see
 * [EmbassyThreadCreationReport]. The backend gives up after a minute, so the bot drops an event older than that.
 */
@Serializable
@SerialName("embassy_thread_creation")
data class EmbassyThreadCreationEvent(
    val creationId: Long,
    val channelId: String,
    val name: String,
    val countryName: String,
    override val occurredAt: Long,
) : BitsEvent

/** What became of an [EmbassyThreadCreationEvent]: the thread, or why there is none. */
@Serializable
data class EmbassyThreadCreationReport(
    /** `null` if [failure] is set. */
    val thread: CreatedThread? = null,
    val failure: ThreadCreationFailure? = null,
)

@Serializable
data class CreatedThread(
    val id: String,
    val name: String,
    val parentChannelId: String,
    val parentChannelName: String,
)

@Serializable
enum class ThreadCreationFailure {
    /** The channel was deleted, or the bot cannot see it. */
    CHANNEL_MISSING,

    /** The bot may not create private threads, manage threads or send messages in threads there. */
    NO_PERMISSION,

    /** Discord refused for another reason. */
    FAILED,
}

/**
 * An admin asked to rename the embassy threads after the name template: the bot renames each thread in [threads], one
 * after the other, unless it is archived or has the name already. It reports only what failed, see
 * [EmbassyThreadRenameReport].
 */
@Serializable
@SerialName("embassy_thread_rename")
data class EmbassyThreadRenameEvent(
    val threads: List<EmbassyThreadRename>,
    override val occurredAt: Long,
) : BitsEvent

@Serializable
data class EmbassyThreadRename(
    val threadId: String,
    /** Channel the thread is in, for finding it while it is archived. */
    val parentChannelId: String,
    val name: String,
    val countryName: String,
)

/** The threads of an [EmbassyThreadRenameEvent] the bot could not rename; the others are renamed or skipped. */
@Serializable
data class EmbassyThreadRenameReport(
    val failures: List<FailedThreadRename>,
)

@Serializable
data class FailedThreadRename(
    val threadId: String,
    /** The name the thread keeps; `null` if it is gone. */
    val threadName: String?,
    val countryName: String,
    val failure: ThreadRenameFailure,
)

@Serializable
enum class ThreadRenameFailure {
    /** Deleted, or the bot cannot see it. */
    MISSING,

    /** The bot may not manage threads in its channel. */
    NO_PERMISSION,

    /** Discord refused for another reason. */
    FAILED,
}
