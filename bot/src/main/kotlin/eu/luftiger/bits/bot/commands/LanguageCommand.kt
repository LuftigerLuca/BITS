package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.slf4j.LoggerFactory

/** `/language` stores the language of what BITS writes only to the member; it works without a linked account. */
class LanguageCommand(
    private val languages: Languages,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        val chosen =
            when (event.getOption(OPTION_LANGUAGE)?.asString) {
                CHOICE_GERMAN -> Language.GERMAN
                CHOICE_ENGLISH -> Language.ENGLISH
                else -> null
            }
        event.deferReply(true).queue()
        scope.launch {
            val message =
                try {
                    languages.set(event.user.id, chosen)
                    // In the new language, which for automatic is that of the client.
                    savedMessage(chosen, chosen ?: Languages.ofLocale(event.userLocale))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Setting the language at the backend failed", e)
                    bitsUnavailableMessage(languages.of(event))
                }
            event.hook.sendMessage(message).queue()
        }
    }

    companion object {
        const val NAME = "language"
        private const val OPTION_LANGUAGE = "language"
        private const val CHOICE_GERMAN = "de"
        private const val CHOICE_ENGLISH = "en"
        private const val CHOICE_AUTOMATIC = "auto"

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Sets the language of what BITS writes only to you")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Legt die Sprache fest, in der BITS nur an dich schreibt")
                .addOptions(
                    OptionData(OptionType.STRING, OPTION_LANGUAGE, "Automatic follows your Discord language", true)
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Automatisch richtet sich nach deiner Discord-Sprache")
                        .addChoices(
                            Command.Choice("Deutsch", CHOICE_GERMAN),
                            Command.Choice("English", CHOICE_ENGLISH),
                            Command.Choice("Automatic", CHOICE_AUTOMATIC).setNameLocalization(DiscordLocale.GERMAN, "Automatisch"),
                        ),
                )

        internal fun savedMessage(
            chosen: Language?,
            language: Language,
        ): String =
            if (chosen == null) {
                language.text(
                    "BITS richtet sich jetzt nach deiner Discord-Sprache. Direktnachrichten kommen auf Deutsch.",
                    "BITS now follows your Discord language. Direct messages arrive in German.",
                )
            } else {
                language.text(
                    "Gespeichert: BITS schreibt dir ab jetzt auf Deutsch, wenn nur du es liest. Öffentliche Nachrichten bleiben, wie " +
                        "sie sind. BITS speichert dafür deine Discord-ID, bis du `/$NAME` auf Automatisch stellst oder den Server verlässt.",
                    "Saved: from now on, BITS writes to you in English whenever only you read it. Public messages stay as they are. " +
                        "For this, BITS stores your Discord ID until you set `/$NAME` to Automatic or leave the server.",
                )
            }
    }
}
