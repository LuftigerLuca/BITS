package eu.luftiger.bits.bot.honeypot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageType
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/** Hands messages to the [Honeypot], without those it never acts on. */
class HoneypotListener(
    private val guildId: String,
    private val honeypot: Honeypot,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!event.isFromGuild || event.guild.id != guildId || !honeypot.watches(event.channel.id)) return
        val member = event.member ?: return
        if (!actsOn(event.message, member)) return
        val message =
            HoneypotMessage(
                event.channel.id,
                event.messageId,
                event.author.id,
                member.effectiveName,
                event.guild.selfMember.canInteract(member),
            )
        scope.launch { honeypot.handle(message) }
    }

    override fun onMessageDelete(event: MessageDeleteEvent) = honeypot.warningDeleted(event.messageId)

    override fun onMessageBulkDelete(event: MessageBulkDeleteEvent) = event.messageIds.forEach(honeypot::warningDeleted)

    internal companion object {
        /** Not bots or webhooks, not admins, and not what Discord posts in a member's name, such as join messages. */
        fun actsOn(
            message: Message,
            member: Member,
        ) = !message.isWebhookMessage &&
            !message.author.isBot &&
            !message.author.isSystem &&
            message.type in setOf(MessageType.DEFAULT, MessageType.INLINE_REPLY) &&
            !member.isOwner &&
            !member.hasPermission(Permission.ADMINISTRATOR)
    }
}
