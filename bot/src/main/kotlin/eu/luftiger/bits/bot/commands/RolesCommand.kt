package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.BackendTimeoutException
import eu.luftiger.bits.bot.backend.TooManyRequestsException
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.ManagedRoleExplanation
import eu.luftiger.bits.shared.ManagedRoleOutcome
import eu.luftiger.bits.shared.RoleExplanationResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory

/**
 * `/roles` tells the user, and only them, for each role BITS manages whether they meet it and why. The backend
 * judges with fresh WarEra data; nothing changes until the next sync.
 */
class RolesCommand(
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
                    explanationMessage(backend.explainRoles(event.user.id, language), language)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: WareraUnavailableException) {
                    wareraUnavailableMessage(language)
                } catch (e: TooManyRequestsException) {
                    tooManyRequestsMessage(e, language)
                } catch (e: BackendTimeoutException) {
                    backendTimeoutMessage(language)
                } catch (e: Exception) {
                    log.error("Role explanation call to backend failed", e)
                    bitsUnavailableMessage(language)
                }
            event.hook.sendMessage(message).queue()
        }
    }

    companion object {
        const val NAME = "roles"

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Explains which roles BITS gives you and why")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Erklärt, welche Rollen dir BITS gibt und warum")

        internal fun explanationMessage(
            response: RoleExplanationResponse,
            language: Language,
        ): String =
            when (response) {
                RoleExplanationResponse.NotLinked ->
                    language.text(
                        "Dein Discord-Account ist mit keinem WarEra-Account verknüpft. Mit `/${VerifyCommand.NAME}` verknüpfst du ihn " +
                            "und bekommst die passenden Rollen.",
                        "Your Discord account is not linked to a WarEra account. Link it with `/${VerifyCommand.NAME}` to get the " +
                            "matching roles.",
                    )
                is RoleExplanationResponse.Explained ->
                    if (response.roles.isEmpty()) {
                        language.text("BITS vergibt gerade keine Rollen.", "BITS does not give any roles at the moment.")
                    } else {
                        explained(response, language)
                    }
            }

        private fun explained(
            response: RoleExplanationResponse.Explained,
            language: Language,
        ): String {
            val name = MarkdownSanitizer.escape(response.wareraUsername)
            val header =
                language.text(
                    "Deine Rollen nach den aktuellen WarEra-Daten von **$name**:\n\n",
                    "Your roles according to the current WarEra data of **$name**:\n\n",
                )
            val footer =
                language.text(
                    "\n\nWeichen deine Rollen davon ab, gleicht BITS sie beim nächsten Abgleich an, spätestens in einer Stunde.",
                    "\n\nIf your roles differ from this, BITS brings them in line at the next sync, within an hour at the latest.",
                )
            val lines = response.roles.map { line(it, language) }
            // Discord takes at most 2000 characters per message; a guild with that many managed roles is unlikely.
            val room = Message.MAX_CONTENT_LENGTH - header.length - footer.length - MORE_ROLES_RESERVE
            var used = 0
            val shown = lines.takeWhile { line -> (used + line.length + 1 <= room).also { if (it) used += line.length + 1 } }
            val hidden = lines.size - shown.size
            val more = if (hidden > 0) language.text("\n… und $hidden weitere", "\n… and $hidden more") else ""
            return header + shown.joinToString("\n") + more + footer
        }

        private fun line(
            role: ManagedRoleExplanation,
            language: Language,
        ): String {
            val mention = "<@&${role.roleId}>"
            return when (role.outcome) {
                ManagedRoleOutcome.MET -> "✅ $mention: ${role.rules.first { it.met == true }.label}"
                ManagedRoleOutcome.NOT_MET -> {
                    val missing = role.rules.mapNotNull { it.detail }
                    val hint = if (missing.size > 1) language.text(" (eins davon genügt)", " (one of these is enough)") else ""
                    "❌ $mention: ${missing.joinToString(", ")}$hint"
                }
                ManagedRoleOutcome.UNDECIDED -> {
                    val unknown = role.rules.filter { it.met == null }.mapNotNull { it.detail }
                    val reasons = unknown.joinToString(", ")
                    "❔ $mention: " +
                        language.text(
                            "gerade nicht prüfbar ($reasons), die Rolle bleibt, wie sie ist",
                            "cannot be checked right now ($reasons), the role stays as it is",
                        )
                }
            }
        }

        private const val MORE_ROLES_RESERVE = 32
    }
}
