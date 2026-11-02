package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.language.Languages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.slf4j.LoggerFactory

/** Test command `/ping`: calls the backend and shows its answer. */
class PingCommand(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
    private val languages: Languages,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        event.deferReply(true).queue()
        scope.launch {
            val language = languages.of(event)
            val message =
                try {
                    val response = backend.ping(event.user.id)
                    val countries =
                        response.wareraCountryCount?.let { language.text("$it Länder", "$it countries") }
                            ?: language.text("nicht erreichbar", "not reachable")
                    language.text("Pong! Pings in den letzten 30 Tagen: ", "Pong! Pings in the last 30 days: ") +
                        "${response.pingCount}, WarEra: $countries"
                } catch (e: Exception) {
                    log.error("Ping to backend failed", e)
                    bitsUnavailableMessage(language)
                }
            event.hook.sendMessage(message).queue()
        }
    }

    companion object {
        const val NAME = "ping"

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Checks whether BITS can be reached")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Prüft, ob BITS erreichbar ist")
    }
}
