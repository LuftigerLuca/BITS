package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.UnlinkAccountResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory

/**
 * `/unverify` deletes the link of the user to their WarEra account, together with any open code. The backend then
 * syncs the member, who loses the roles BITS manages. No confirmation, since `/verify` links again at any time.
 */
class UnverifyCommand(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        event.deferReply(true).queue()
        scope.launch {
            val message =
                try {
                    unlinkedMessage(backend.unlinkAccount(event.user.id))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Unlinking at the backend failed", e)
                    BITS_UNAVAILABLE_MESSAGE
                }
            event.hook.sendMessage(message).queue()
        }
    }

    companion object {
        const val NAME = "unverify"

        val data: SlashCommandData = Commands.slash(NAME, "Löscht die Verknüpfung deines Discord-Accounts mit WarEra")

        internal fun unlinkedMessage(response: UnlinkAccountResponse): String =
            response.wareraUsername?.let {
                "Die Verknüpfung mit **${MarkdownSanitizer.escape(it)}** ist gelöscht. Die Rollen, die BITS vergibt, verlierst du " +
                    "in den nächsten Minuten. Mit `/${VerifyCommand.NAME}` kannst du dich jederzeit wieder verknüpfen."
            } ?: "Dein Discord-Account ist mit keinem WarEra-Account verknüpft."
    }
}
