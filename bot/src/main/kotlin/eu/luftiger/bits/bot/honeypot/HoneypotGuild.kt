package eu.luftiger.bits.bot.honeypot

import eu.luftiger.bits.shared.HoneypotWarning
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder

/** What the honeypot needs from Discord; every call fails with what JDA throws. */
interface HoneypotGuild {
    /** Returns the ID of the new message; fails with [HoneypotChannelGone] if there is no such channel. */
    suspend fun post(
        channelId: String,
        embed: MessageEmbed,
    ): String

    /**
     * Whether the message is still there, `false` also if its channel is gone; if it is, it now shows [embed], so a warning
     * posted by an older version gets the current text.
     */
    suspend fun update(
        warning: HoneypotWarning,
        embed: MessageEmbed,
    ): Boolean

    /** Succeeds as well if the message or its channel is gone already. */
    suspend fun delete(
        channelId: String,
        messageId: String,
    )

    fun mayKick(): Boolean

    /** Succeeds as well if the member is gone already. */
    suspend fun kick(discordUserId: String)
}

class HoneypotChannelGone : Exception()

class JdaHoneypotGuild(
    private val jda: JDA,
    private val guildId: String,
) : HoneypotGuild {
    private val guild: Guild
        get() = jda.getGuildById(guildId) ?: error("Bot is not a member of guild $guildId")

    private fun channel(channelId: String) = guild.getChannelById(GuildMessageChannel::class.java, channelId)

    override suspend fun post(
        channelId: String,
        embed: MessageEmbed,
    ): String {
        val channel = channel(channelId) ?: throw HoneypotChannelGone()
        return channel
            .sendMessageEmbeds(embed)
            .submit()
            .await()
            .id
    }

    override suspend fun update(
        warning: HoneypotWarning,
        embed: MessageEmbed,
    ): Boolean {
        val channel = channel(warning.channelId) ?: return false
        return try {
            val message = channel.retrieveMessageById(warning.messageId).submit().await()
            val shown = message.embeds.singleOrNull()
            // Only if needed: an edit is a request of its own, and Discord marks the message as edited.
            if (message.contentRaw.isNotEmpty() || shown?.title != embed.title || shown?.description != embed.description) {
                val edit =
                    MessageEditBuilder()
                        .setContent("")
                        .setEmbeds(embed)
                        .build()
                message.editMessage(edit).submit().await()
            }
            true
        } catch (e: ErrorResponseException) {
            if (e.errorResponse != ErrorResponse.UNKNOWN_MESSAGE && e.errorResponse != ErrorResponse.UNKNOWN_CHANNEL) throw e
            false
        }
    }

    override suspend fun delete(
        channelId: String,
        messageId: String,
    ) {
        val channel = channel(channelId) ?: return
        try {
            channel.deleteMessageById(messageId).submit().await()
        } catch (e: ErrorResponseException) {
            if (e.errorResponse != ErrorResponse.UNKNOWN_MESSAGE && e.errorResponse != ErrorResponse.UNKNOWN_CHANNEL) throw e
        }
    }

    override fun mayKick(): Boolean = guild.selfMember.hasPermission(Permission.KICK_MEMBERS)

    override suspend fun kick(discordUserId: String) {
        try {
            guild
                .kick(UserSnowflake.fromId(discordUserId))
                .reason(KICK_AUDIT_REASON)
                .submit()
                .await()
        } catch (e: ErrorResponseException) {
            if (e.errorResponse != ErrorResponse.UNKNOWN_MEMBER) throw e
        }
    }

    private companion object {
        /** Shown in Discord's audit log. */
        const val KICK_AUDIT_REASON = "BITS: Nachricht im Honeypot-Kanal"
    }
}
