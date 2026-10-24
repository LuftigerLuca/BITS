package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.BackendTimeoutException
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.VerificationCheckedEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * `/verify <name>` links the Discord account to a WarEra account: the user puts a one-time code into the name of
 * one of their companies and confirms with the button "Prüfen".
 *
 * The button carries the code, so it keeps working after a restart of the bot, and the backend can tell an old
 * button from the current one. The backend looks for the code in the background, since WarEra may be slow, and sends
 * the result as [VerificationCheckedEvent]; until then Discord shows that the bot is thinking.
 */
class VerifyCommand(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pendingChecks =
        PendingChecks<InteractionHook>(scope, CHECK_RESULT_TIMEOUT) { hook ->
            hook.sendMessage(CHECK_TIMED_OUT_MESSAGE).queue()
        }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        val username = event.getOption(OPTION_NAME)?.asString.orEmpty()
        event.deferReply(true).queue()
        respond(event.hook) {
            when (val response = backend.startVerification(event.user.id, username)) {
                is StartVerificationResponse.Started ->
                    event.hook
                        .sendMessage(startedMessage(response))
                        .addComponents(ActionRow.of(Button.primary(CHECK_BUTTON_PREFIX + response.code, "Prüfen")))
                        .queue()
                StartVerificationResponse.UserNotFound ->
                    event.hook
                        .sendMessage(
                            "Ich habe keinen WarEra-Account mit dem Namen **${escape(username)}** gefunden. Prüfe die Schreibweise.",
                        ).queue()
                is StartVerificationResponse.NoCompany ->
                    event.hook
                        .sendMessage(
                            "**${escape(response.wareraUsername)}** hat keine Firma. Zum Verifizieren brauchst du mindestens eine " +
                                "Firma in WarEra, weil der Code in ihren Namen kommt.",
                        ).queue()
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val code = event.componentId.removePrefix(CHECK_BUTTON_PREFIX).takeIf { it != event.componentId } ?: return
        event.deferReply(true).queue()
        // Before the call, since the result may arrive before the answer does.
        pendingChecks.add(code, event.hook)
        respond(event.hook, onFailure = { pendingChecks.complete(code) }) {
            val response = backend.checkVerification(event.user.id, code)
            if (response != CheckVerificationResponse.Checking) {
                pendingChecks.complete(code)
                event.hook.sendMessage(checkMessage(response)).queue()
            }
        }
    }

    /** Answers the click on "Prüfen" that started the check. */
    fun onChecked(event: VerificationCheckedEvent) {
        val hook = pendingChecks.complete(event.code) ?: return log.info("No one waits for the check of user {}", event.discordUserId)
        hook.sendMessage(checkMessage(event.result)).queue()
    }

    private fun respond(
        hook: InteractionHook,
        onFailure: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure()
                when (e) {
                    is WareraUnavailableException -> hook.sendMessage(WARERA_UNAVAILABLE_MESSAGE).queue()
                    is BackendTimeoutException ->
                        hook.sendMessage("Das dauert gerade länger als erwartet. Versuche es gleich noch einmal.").queue()
                    else -> {
                        log.error("Verification call to backend failed", e)
                        hook.sendMessage(BITS_UNAVAILABLE_MESSAGE).queue()
                    }
                }
            }
        }
    }

    companion object {
        const val NAME = "verify"
        private const val OPTION_NAME = "name"
        private const val CHECK_BUTTON_PREFIX = "verify:check:"

        /**
         * Longest wait for the result of a check. The backend needs a few WarEra calls; even with throttling and
         * retries they rarely take a minute. Discord accepts replies for 15 minutes.
         */
        private val CHECK_RESULT_TIMEOUT: Duration = Duration.ofMinutes(2)

        private const val WARERA_UNAVAILABLE_MESSAGE =
            "WarEra ist gerade nicht erreichbar. Versuche es gleich noch einmal, ein offener Code bleibt gültig."

        internal const val CHECK_TIMED_OUT_MESSAGE =
            "Die Prüfung dauert ungewöhnlich lange. Klicke gleich noch einmal auf „Prüfen“, ein offener Code bleibt gültig."

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Verknüpft deinen Discord-Account mit deinem WarEra-Account")
                .addOption(OptionType.STRING, OPTION_NAME, "Dein Name in WarEra", true)

        internal fun startedMessage(response: StartVerificationResponse.Started): String {
            val expiresAt = response.expiresAt / 1000
            val hint =
                response.linkedWareraUsername
                    ?.let {
                        "Hinweis: Dein Discord-Account ist bereits mit **${escape(it)}** verknüpft. Wenn du fortfährst, wird diese " +
                            "Verknüpfung ersetzt.\n\n"
                    }.orEmpty()
            return hint +
                "Dein Code für **${escape(response.wareraUsername)}**: `${response.code}`\n\n" +
                // WarEra allows at most 20 characters per company name, so appending the code rarely fits.
                "1. Benenne in WarEra eine deiner Firmen vorübergehend in `${response.code}` um. Firmennamen haben höchstens " +
                "20 Zeichen, weiterer Text neben dem Code ist aber erlaubt.\n" +
                "2. Klicke danach auf „Prüfen“.\n\n" +
                "Der Code ist gültig bis <t:$expiresAt:t> (<t:$expiresAt:R>). Nach der Verknüpfung kannst du ihn wieder aus dem " +
                "Firmennamen entfernen.\n\n" +
                SOCIAL_ENGINEERING_WARNING
        }

        /** The code proves who renamed the company, not who asked for it; see VerificationService in the backend. */
        internal const val SOCIAL_ENGINEERING_WARNING =
            "⚠️ Trage nur Codes ein, die du selbst mit `/verify` bekommen hast. Bittet dich jemand, einen Code in einen " +
                "Firmennamen zu schreiben, etwa für ein Gewinnspiel, will er deinen WarEra-Account mit seinem Discord-Account " +
                "verknüpfen und deine Rollen bekommen."

        internal fun checkMessage(response: CheckVerificationResponse): String =
            when (response) {
                is CheckVerificationResponse.Linked ->
                    "Fertig! Dein Discord-Account ist jetzt mit **${escape(response.wareraUsername)}** verknüpft. Deine Rollen " +
                        "bekommst du in den nächsten Minuten automatisch. Du kannst den Code wieder aus dem Firmennamen entfernen."
                // Answered by the event, never shown.
                CheckVerificationResponse.Checking -> "Ich prüfe deinen Code …"
                CheckVerificationResponse.WareraUnavailable -> WARERA_UNAVAILABLE_MESSAGE
                CheckVerificationResponse.CodeMissing ->
                    "Ich habe den Code in keinem deiner Firmennamen gefunden. Prüfe, ob die Umbenennung gespeichert ist, und versuche es " +
                        "noch einmal."
                CheckVerificationResponse.Expired -> "Der Code ist abgelaufen. Starte bitte mit `/verify` neu."
                CheckVerificationResponse.Invalid -> "Dieser Code ist nicht mehr gültig. Starte bitte mit `/verify` neu."
                is CheckVerificationResponse.TooEarly ->
                    "Bitte warte noch ${response.retryAfterSeconds} Sekunden, bevor du erneut prüfst."
            }

        private fun escape(text: String) = MarkdownSanitizer.escape(text)
    }
}
