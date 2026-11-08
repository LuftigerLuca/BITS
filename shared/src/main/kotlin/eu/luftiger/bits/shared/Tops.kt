package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** All forum channels of the guild as the bot sees them; replaces whatever the bot reported before. */
@Serializable
data class DiscordForumsReport(
    val forums: List<DiscordForumDto>,
)

@Serializable
data class DiscordForumDto(
    val id: String,
    val name: String,
    /** Name of the category the forum is in, `null` for none. */
    val category: String?,
    /** Position in the channel list, categories first. */
    val position: Int,
    /** Whether the bot may lock, archive and tag the posts there and post embeds in them, as `/topclose` needs. */
    val threadsManageable: Boolean,
    /** The tags posts can carry, in the order Discord shows them. */
    val tags: List<ForumTagDto> = emptyList(),
)

@Serializable
data class ForumTagDto(
    val id: String,
    val name: String,
)

/** The member who uses `/topclose` or `/topopen`, as the backend needs them to decide whether they may. */
@Serializable
data class TopActor(
    val discordUserId: String,
    /** As Discord shows the member, for the audit log. */
    val name: String,
    val avatarUrl: String?,
    val roleIds: List<String>,
    /** Owner of the guild or holder of the Administrator permission. */
    val administrator: Boolean,
)

/**
 * Asks whether [actor] may close or reopen a post of the forum [forumId] as a TOP: admins always, others with one of the
 * roles set for TOPs in the dashboard.
 */
@Serializable
data class TopPermissionRequest(
    val actor: TopActor,
    val forumId: String,
)

/** Outcome of [TopPermissionRequest]. Each subtype is identified by its `type` field. */
@Serializable
sealed interface TopPermissionResponse {
    @Serializable
    @SerialName("allowed")
    data class Allowed(
        /** The tag a closed TOP gets; `null` for none. */
        val doneTagId: String?,
    ) : TopPermissionResponse

    @Serializable
    @SerialName("forbidden")
    data object Forbidden : TopPermissionResponse

    /** The forum is not one of the TOP forums. */
    @Serializable
    @SerialName("not_top_forum")
    data object NotTopForum : TopPermissionResponse
}

@Serializable
enum class TopAction {
    CLOSED,
    REOPENED,
}

/** What the bot did to a TOP post after [TopPermissionRequest] allowed it, for the audit log. */
@Serializable
data class TopActionReport(
    val actor: TopActor,
    val forumId: String,
    val threadId: String,
    val threadName: String,
    val action: TopAction,
    /** The reason given with `/topclose`; `null` for none. */
    val reason: String? = null,
    /** Whether `/topclose` archived the post as well. */
    val archived: Boolean = false,
)
