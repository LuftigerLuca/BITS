package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.BackendTimeoutException
import eu.luftiger.bits.bot.backend.TooManyRequestsException
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.Language
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
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * `/verify <name>` links the Discord account to a WarEra account: the user puts a one-time code into the name of
 * one of their companies and confirms with the check button.
 *
 * The button carries the code, so it keeps working after a restart of the bot, and the backend can tell an old
 * button from the current one. The backend looks for the code in the background, since WarEra may be slow, and sends
 * the result as [VerificationCheckedEvent]; until then Discord shows that the bot is thinking.
 */
class VerifyCommand(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
    private val languages: Languages,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val pendingChecks =
        PendingChecks<PendingCheck>(scope, CHECK_RESULT_TIMEOUT) { check ->
            check.hook.sendMessage(checkTimedOutMessage(check.language)).queue()
        }

    /** The event with the result knows neither the client of the member nor their language. */
    private class PendingCheck(
        val hook: InteractionHook,
        val language: Language,
    )

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        val username = event.getOption(OPTION_NAME)?.asString.orEmpty()
        event.deferReply(true).queue()
        respond(event, event.hook) { language ->
            when (val response = backend.startVerification(event.user.id, username)) {
                is StartVerificationResponse.Started ->
                    event.hook
                        .sendMessage(startedMessage(response, language))
                        .addComponents(ActionRow.of(Button.primary(CHECK_BUTTON_PREFIX + response.code, checkLabel(language))))
                        .queue()
                StartVerificationResponse.UserNotFound ->
                    event.hook
                        .sendMessage(
                            language.text(
                                "Ich habe keinen WarEra-Account mit dem Namen **${escape(username)}** gefunden. Prüfe die Schreibweise.",
                                "I could not find a WarEra account named **${escape(username)}**. Please check the spelling.",
                            ),
                        ).queue()
                is StartVerificationResponse.NoCompany ->
                    event.hook
                        .sendMessage(
                            language.text(
                                "**${escape(response.wareraUsername)}** hat keine Firma. Zum Verifizieren brauchst du mindestens eine " +
                                    "Firma in WarEra, weil der Code in ihren Namen kommt.",
                                "**${escape(response.wareraUsername)}** has no company. To verify, you need at least one company in " +
                                    "WarEra, because the code goes into its name.",
                            ),
                        ).queue()
            }
        }
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val code = event.componentId.removePrefix(CHECK_BUTTON_PREFIX).takeIf { it != event.componentId } ?: return
        event.deferReply(true).queue()
        respond(event, event.hook, onFailure = { pendingChecks.complete(code) }) { language ->
            // Before the call, since the result may arrive before the answer does.
            pendingChecks.add(code, PendingCheck(event.hook, language))
            val response = backend.checkVerification(event.user.id, code, event.member?.effectiveName ?: event.user.effectiveName)
            if (response != CheckVerificationResponse.Checking) {
                pendingChecks.complete(code)
                sendCheckResult(event.hook, response, language)
            }
        }
    }

    /** Answers the click on the button that started the check. */
    fun onChecked(event: VerificationCheckedEvent) {
        val check = pendingChecks.complete(event.code) ?: return log.info("No one waits for the check of user {}", event.discordUserId)
        sendCheckResult(check.hook, event.result, check.language)
    }

    /** A new link comes with the offer to take the WarEra name as nickname and the question for the language. */
    private fun sendCheckResult(
        hook: InteractionHook,
        response: CheckVerificationResponse,
        language: Language,
    ) {
        val message = hook.sendMessage(checkMessage(response, language))
        if (response is CheckVerificationResponse.Linked) {
            message.addComponents(ActionRow.of(NicknameCommand.optInButton(language)), ActionRow.of(LanguageCommand.languageButtons))
        }
        message.queue()
    }

    private fun respond(
        interaction: Interaction,
        hook: InteractionHook,
        onFailure: () -> Unit = {},
        block: suspend (Language) -> Unit,
    ) {
        scope.launch {
            val language = languages.of(interaction)
            try {
                block(language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure()
                when (e) {
                    is WareraUnavailableException -> hook.sendMessage(wareraUnavailableWithCode(language)).queue()
                    is TooManyRequestsException -> hook.sendMessage(tooManyRequestsMessage(e, language)).queue()
                    is BackendTimeoutException -> hook.sendMessage(backendTimeoutMessage(language)).queue()
                    else -> {
                        log.error("Verification call to backend failed", e)
                        hook.sendMessage(bitsUnavailableMessage(language)).queue()
                    }
                }
            }
        }
    }

    companion object {
        const val NAME = "verify"
        private const val OPTION_NAME = "name"
        private const val MAX_NAME_LENGTH = 32
        const val CHECK_BUTTON_PREFIX = "verify:check:"

        /**
         * Longest wait for the result of a check. The backend needs a few WarEra calls; even with throttling and
         * retries they rarely take a minute. Discord accepts replies for 15 minutes.
         */
        private val CHECK_RESULT_TIMEOUT: Duration = Duration.ofMinutes(2)

        private fun checkLabel(language: Language) = language.text("Prüfen", "Check")

        private fun wareraUnavailableWithCode(language: Language) =
            wareraUnavailableMessage(language) + " " + language.text("Ein offener Code bleibt gültig.", "An open code stays valid.")

        internal fun checkTimedOutMessage(language: Language): String =
            language.text(
                "Die Prüfung dauert ungewöhnlich lange. Klicke gleich noch einmal auf „Prüfen“, ein offener Code bleibt gültig.",
                "The check is taking unusually long. Click “Check” again shortly, an open code stays valid.",
            )

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Links your Discord account to your WarEra account")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Verknüpft deinen Discord-Account mit deinem WarEra-Account")
                // The backend rejects longer names, so Discord should not let them through.
                .addOptions(
                    OptionData(OptionType.STRING, OPTION_NAME, "Your name in WarEra", true)
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Dein Name in WarEra")
                        .setMaxLength(MAX_NAME_LENGTH),
                )

        internal fun startedMessage(
            response: StartVerificationResponse.Started,
            language: Language,
        ): String {
            val expiresAt = response.expiresAt / 1000
            val account = escape(response.wareraUsername)
            val code = response.code
            val hint =
                response.linkedWareraUsername
                    ?.let {
                        language.text(
                            "Hinweis: Dein Discord-Account ist bereits mit **${escape(it)}** verknüpft. Wenn du fortfährst, wird diese " +
                                "Verknüpfung ersetzt.\n\n",
                            "Note: Your Discord account is already linked to **${escape(it)}**. If you continue, this link will be " +
                                "replaced.\n\n",
                        )
                    }.orEmpty()
            return hint +
                // WarEra allows at most 20 characters per company name, so appending the code rarely fits.
                language.text(
                    "Dein Code für **$account**: `$code`\n\n" +
                        "1. Benenne in WarEra eine deiner Firmen vorübergehend in `$code` um. Firmennamen haben höchstens " +
                        "20 Zeichen, weiterer Text neben dem Code ist aber erlaubt.\n" +
                        "2. Klicke danach auf „Prüfen“.\n\n" +
                        "Der Code ist gültig bis <t:$expiresAt:t> (<t:$expiresAt:R>). Nach der Verknüpfung kannst du ihn wieder aus dem " +
                        "Firmennamen entfernen.",
                    "Your code for **$account**: `$code`\n\n" +
                        "1. In WarEra, temporarily rename one of your companies to `$code`. Company names have at most 20 characters, " +
                        "but other text next to the code is allowed.\n" +
                        "2. Then click “Check”.\n\n" +
                        "The code is valid until <t:$expiresAt:t> (<t:$expiresAt:R>). After linking, you can remove it from the " +
                        "company name again.",
                ) + "\n\n" +
                privacyNotice(language) + "\n\n" +
                socialEngineeringWarning(language)
        }

        /** What the data protection section of the README says, in short. */
        internal fun privacyNotice(language: Language): String =
            language.text(
                "BITS speichert deine Discord-ID zusammen mit deinem WarEra-Account, um dir passende Rollen zu geben. Mit " +
                    "`/${ProfileCommand.NAME}` können Mitglieder des Servers dein WarEra-Profil sehen. Mit `/${UnverifyCommand.NAME}` " +
                    "löschst du die Verknüpfung jederzeit. Tools, denen Admins ein API-Token dafür geben, können sie ebenfalls lesen.",
                "BITS stores your Discord ID together with your WarEra account to give you the matching roles. With " +
                    "`/${ProfileCommand.NAME}`, members of the server can see your WarEra profile. With `/${UnverifyCommand.NAME}`, " +
                    "you can delete the link at any time. Tools that admins give an API token for it can read it as well.",
            )

        /** The code proves who renamed the company, not who asked for it; see VerificationService in the backend. */
        internal fun socialEngineeringWarning(language: Language): String =
            language.text(
                "⚠️ Trage nur Codes ein, die du selbst mit `/$NAME` bekommen hast. Bittet dich jemand, einen Code in einen " +
                    "Firmennamen zu schreiben, etwa für ein Gewinnspiel, will er deinen WarEra-Account mit seinem Discord-Account " +
                    "verknüpfen und deine Rollen bekommen.",
                "⚠️ Only enter codes you got yourself with `/$NAME`. If somebody asks you to put a code into a company name, for " +
                    "example for a giveaway, they want to link your WarEra account to their Discord account and get your roles.",
            )

        internal fun checkMessage(
            response: CheckVerificationResponse,
            language: Language,
        ): String =
            when (response) {
                is CheckVerificationResponse.Linked -> {
                    val account = escape(response.wareraUsername)
                    language.text(
                        "Fertig! Dein Discord-Account ist jetzt mit **$account** verknüpft. Deine Rollen bekommst du in den nächsten " +
                            "Minuten automatisch. Du kannst den Code wieder aus dem Firmennamen entfernen.\n\n" +
                            "Möchtest du deinen WarEra-Namen auch als Nickname tragen? Klicke auf „Nickname übernehmen“ oder nutze " +
                            "jederzeit `/${NicknameCommand.NAME}`.\n\n" +
                            "In welcher Sprache soll BITS dir schreiben, wenn nur du es liest? Wähle unten eine Sprache; ohne Wahl " +
                            "richtet sich BITS nach deiner Discord-Sprache. Ändern kannst du das jederzeit mit `/${LanguageCommand.NAME}`.",
                        "Done! Your Discord account is now linked to **$account**. You will get your roles automatically within the " +
                            "next few minutes. You can remove the code from the company name again.\n\n" +
                            "Would you like to use your WarEra name as your nickname too? Click “Use as nickname” or use " +
                            "`/${NicknameCommand.NAME}` at any time.\n\n" +
                            "Which language should BITS use when only you read it? Choose one below; without a choice, BITS follows your " +
                            "Discord language. You can change this at any time with `/${LanguageCommand.NAME}`.",
                    )
                }
                // Answered by the event, never shown.
                CheckVerificationResponse.Checking -> language.text("Ich prüfe deinen Code …", "Checking your code …")
                CheckVerificationResponse.WareraUnavailable -> wareraUnavailableWithCode(language)
                CheckVerificationResponse.CodeMissing ->
                    language.text(
                        "Ich habe den Code in keinem deiner Firmennamen gefunden. Prüfe, ob die Umbenennung gespeichert ist, und " +
                            "versuche es noch einmal.",
                        "I did not find the code in any of your company names. Check that the new name is saved and try again.",
                    )
                CheckVerificationResponse.Expired ->
                    language.text(
                        "Der Code ist abgelaufen. Starte bitte mit `/$NAME` neu.",
                        "The code has expired. Please start again with `/$NAME`.",
                    )
                CheckVerificationResponse.Invalid ->
                    language.text(
                        "Dieser Code ist nicht mehr gültig. Starte bitte mit `/$NAME` neu.",
                        "This code is no longer valid. Please start again with `/$NAME`.",
                    )
                is CheckVerificationResponse.TooEarly ->
                    language.text(
                        "Bitte warte noch ${response.retryAfterSeconds} Sekunden, bevor du erneut prüfst.",
                        "Please wait another ${response.retryAfterSeconds} seconds before checking again.",
                    )
            }

        private fun escape(text: String) = MarkdownSanitizer.escape(text)
    }
}
