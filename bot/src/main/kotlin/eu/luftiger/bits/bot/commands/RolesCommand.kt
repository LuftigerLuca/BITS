package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.BackendTimeoutException
import eu.luftiger.bits.bot.backend.TooManyRequestsException
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.ManagedRoleExplanation
import eu.luftiger.bits.shared.ManagedRoleOutcome
import eu.luftiger.bits.shared.RoleExplanationResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory

/**
 * `/rollen` tells the user, and only them, for each role BITS manages whether they meet it and why. The backend
 * judges with fresh WarEra data; nothing changes until the next sync.
 */
class RolesCommand(
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
                    explanationMessage(backend.explainRoles(event.user.id))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: WareraUnavailableException) {
                    WARERA_UNAVAILABLE_MESSAGE
                } catch (e: TooManyRequestsException) {
                    tooManyRequestsMessage(e)
                } catch (e: BackendTimeoutException) {
                    "Das dauert gerade länger als erwartet. Versuche es gleich noch einmal."
                } catch (e: Exception) {
                    log.error("Role explanation call to backend failed", e)
                    BITS_UNAVAILABLE_MESSAGE
                }
            event.hook.sendMessage(message).queue()
        }
    }

    companion object {
        const val NAME = "rollen"

        internal const val WARERA_UNAVAILABLE_MESSAGE = "WarEra ist gerade nicht erreichbar. Versuche es gleich noch einmal."

        val data: SlashCommandData = Commands.slash(NAME, "Erklärt, welche Rollen dir BITS gibt und warum")

        internal fun explanationMessage(response: RoleExplanationResponse): String =
            when (response) {
                RoleExplanationResponse.NotLinked ->
                    "Dein Discord-Account ist mit keinem WarEra-Account verknüpft. Mit `/${VerifyCommand.NAME}` verknüpfst du ihn " +
                        "und bekommst die passenden Rollen."
                is RoleExplanationResponse.Explained ->
                    if (response.roles.isEmpty()) {
                        "BITS vergibt gerade keine Rollen."
                    } else {
                        explained(response)
                    }
            }

        private fun explained(response: RoleExplanationResponse.Explained): String {
            val header =
                "Deine Rollen nach den aktuellen WarEra-Daten von **${MarkdownSanitizer.escape(response.wareraUsername)}**:\n\n"
            val footer = "\n\nWeichen deine Rollen davon ab, gleicht BITS sie beim nächsten Abgleich an, spätestens in einer Stunde."
            val lines = response.roles.map(::line)
            // Discord takes at most 2000 characters per message; a guild with that many managed roles is unlikely.
            val room = Message.MAX_CONTENT_LENGTH - header.length - footer.length - MORE_ROLES_RESERVE
            var used = 0
            val shown = lines.takeWhile { line -> (used + line.length + 1 <= room).also { if (it) used += line.length + 1 } }
            val more = if (shown.size < lines.size) "\n… und ${lines.size - shown.size} weitere" else ""
            return header + shown.joinToString("\n") + more + footer
        }

        private fun line(role: ManagedRoleExplanation): String {
            val mention = "<@&${role.roleId}>"
            return when (role.outcome) {
                ManagedRoleOutcome.MET -> "✅ $mention: ${role.rules.first { it.met == true }.label}"
                ManagedRoleOutcome.NOT_MET -> {
                    val missing = role.rules.mapNotNull { it.detail }
                    val hint = if (missing.size > 1) " (eins davon genügt)" else ""
                    "❌ $mention: ${missing.joinToString(", ")}$hint"
                }
                ManagedRoleOutcome.UNDECIDED -> {
                    val unknown = role.rules.filter { it.met == null }.mapNotNull { it.detail }
                    "❔ $mention: gerade nicht prüfbar (${unknown.joinToString(", ")}), die Rolle bleibt, wie sie ist"
                }
            }
        }

        private const val MORE_ROLES_RESERVE = 32
    }
}
