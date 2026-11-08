package eu.luftiger.bits.bot.verifypanel

import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/** Tells the [VerifyPanel] that its message was deleted, so it posts it again with the next poll. */
class VerifyPanelListener(
    private val panel: VerifyPanel,
) : ListenerAdapter() {
    override fun onMessageDelete(event: MessageDeleteEvent) = panel.messageDeleted(event.messageId)

    override fun onMessageBulkDelete(event: MessageBulkDeleteEvent) = event.messageIds.forEach(panel::messageDeleted)
}
