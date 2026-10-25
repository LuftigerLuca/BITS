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
)
