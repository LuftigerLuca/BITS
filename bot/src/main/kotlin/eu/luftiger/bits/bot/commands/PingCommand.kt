package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.slf4j.LoggerFactory

/** Test command `/ping`: calls the backend and shows its answer. */
class PingCommand(
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
                    val response = backend.ping(event.user.id)
                    val countries = response.wareraCountryCount?.let { "$it Länder" } ?: "nicht erreichbar"
                    "Pong! Pings bisher: ${response.pingCount}, WarEra: $countries"
                } catch (e: Exception) {
                    log.error("Ping to backend failed", e)
                    BITS_UNAVAILABLE_MESSAGE
                }
            event.hook.sendMessage(message).queue()
        }
    }

    companion object {
        const val NAME = "ping"

        val data: SlashCommandData = Commands.slash(NAME, "Prüft, ob BITS erreichbar ist")
    }
}
