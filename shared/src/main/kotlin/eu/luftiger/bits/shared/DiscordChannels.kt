package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/** All text channels of the guild as the bot sees them; replaces whatever the bot reported before. */
@Serializable
data class DiscordChannelsReport(
    val channels: List<DiscordChannelDto>,
)

@Serializable
data class DiscordChannelDto(
    val id: String,
    val name: String,
    /** Name of the category the channel is in, `null` for none. */
    val category: String?,
    /** Position in the channel list, categories first. */
    val position: Int,
    /** Whether the bot may post messages with embeds and files there. */
    val postable: Boolean,
    /**
     * Whether @everyone may see the channel. Discord adds only members who may see it to a private thread there. The
     * default stands for a bot that does not tell, so the backend checks nothing then.
     */
    val everyoneCanView: Boolean = true,
    /** Roles that may see the channel; empty if [everyoneCanView]. */
    val viewerRoleIds: List<String> = emptyList(),
    /**
     * Whether the bot may create private threads there and manage them as embassy threads: a text channel where it may
     * create private threads, manage threads and send messages in threads.
     */
    val threadsCreatable: Boolean = false,
)
