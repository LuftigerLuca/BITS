package eu.luftiger.bits.bot.verifypanel

import eu.luftiger.bits.shared.VerifyPanelMessage
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder

/** What the message with the button to verify needs from Discord; every call fails with what JDA throws. */
interface VerifyPanelGuild {
    /** Returns the ID of the new message; fails with [VerifyPanelChannelGone] if there is no such channel. */
    suspend fun post(
        channelId: String,
        embed: MessageEmbed,
        button: Button,
    ): String

    /**
     * Whether the message is still there, `false` also if its channel is gone; if it is, it now shows [embed] and
     * [button], so a message posted by an older version gets the current text.
     */
    suspend fun update(
        message: VerifyPanelMessage,
        embed: MessageEmbed,
        button: Button,
    ): Boolean

    /** Succeeds as well if the message or its channel is gone already. */
    suspend fun delete(message: VerifyPanelMessage)
}

class VerifyPanelChannelGone : Exception()

class JdaVerifyPanelGuild(
    private val jda: JDA,
    private val guildId: String,
) : VerifyPanelGuild {
    private val guild: Guild
        get() = jda.getGuildById(guildId) ?: error("Bot is not a member of guild $guildId")

    private fun channel(channelId: String) = guild.getChannelById(GuildMessageChannel::class.java, channelId)

    override suspend fun post(
        channelId: String,
        embed: MessageEmbed,
        button: Button,
    ): String {
        val channel = channel(channelId) ?: throw VerifyPanelChannelGone()
        return channel
            .sendMessageEmbeds(embed)
            .addComponents(ActionRow.of(button))
            .submit()
            .await()
            .id
    }

    override suspend fun update(
        message: VerifyPanelMessage,
        embed: MessageEmbed,
        button: Button,
    ): Boolean {
        val channel = channel(message.channelId) ?: return false
        return try {
            val shown = channel.retrieveMessageById(message.messageId).submit().await()
            // Only if needed: an edit is a request of its own, and Discord marks the message as edited.
            if (!shows(shown, embed, button)) {
                val edit =
                    MessageEditBuilder()
                        .setContent("")
                        .setEmbeds(embed)
                        .setComponents(ActionRow.of(button))
                        .build()
                shown.editMessage(edit).submit().await()
            }
            true
        } catch (e: ErrorResponseException) {
            if (e.errorResponse != ErrorResponse.UNKNOWN_MESSAGE && e.errorResponse != ErrorResponse.UNKNOWN_CHANNEL) throw e
            false
        }
    }

    override suspend fun delete(message: VerifyPanelMessage) {
        val channel = channel(message.channelId) ?: return
        try {
            channel.deleteMessageById(message.messageId).submit().await()
        } catch (e: ErrorResponseException) {
            if (e.errorResponse != ErrorResponse.UNKNOWN_MESSAGE && e.errorResponse != ErrorResponse.UNKNOWN_CHANNEL) throw e
        }
    }

    private fun shows(
        message: Message,
        embed: MessageEmbed,
        button: Button,
    ): Boolean {
        val shownEmbed = message.embeds.singleOrNull()
        val shownButton = message.componentTree.findAll(Button::class.java).singleOrNull()
        return message.contentRaw.isEmpty() &&
            shownEmbed?.title == embed.title &&
            shownEmbed?.description == embed.description &&
            shownButton?.customId == button.customId &&
            shownButton?.label == button.label
    }
}
