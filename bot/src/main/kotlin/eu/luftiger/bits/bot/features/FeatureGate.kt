package eu.luftiger.bits.bot.features

import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.FeatureStates
import eu.luftiger.bits.shared.Language
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener

/**
 * Passes the events of [delegate] on, unless they belong to a feature an admin switched off: then the command
 * [commandName], only its subcommand [subcommandName] if given, and the buttons whose ID starts with [buttonPrefix] get
 * [disabledMessage] instead, in the language the bot knows without asking, since the reply cannot wait. Other events, such as autocompletion, go through, since they change nothing.
 */
class FeatureGate(
    private val features: Features,
    private val enabled: (FeatureStates) -> Boolean,
    private val commandName: String,
    private val subcommandName: String? = null,
    private val buttonPrefix: String? = null,
    private val languages: Languages,
    private val delegate: EventListener,
) : EventListener {
    override fun onEvent(event: GenericEvent) {
        val gated =
            when (event) {
                is SlashCommandInteractionEvent ->
                    event.name == commandName && (subcommandName == null || event.subcommandName == subcommandName)
                is ButtonInteractionEvent -> buttonPrefix != null && event.componentId.startsWith(buttonPrefix)
                else -> false
            }
        if (gated && !enabled(features.states)) {
            when (event) {
                is SlashCommandInteractionEvent -> event.reply(disabledMessage(languages.cachedOf(event))).setEphemeral(true).queue()
                is ButtonInteractionEvent -> event.reply(disabledMessage(languages.cachedOf(event))).setEphemeral(true).queue()
            }
            return
        }
        delegate.onEvent(event)
    }

    companion object {
        fun disabledMessage(language: Language): String =
            language.text("Diese Funktion ist gerade ausgeschaltet.", "This feature is switched off at the moment.")
    }
}
