package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.NicknamePreferenceResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Member
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

/**
 * `/nickname on|off` lets members choose whether BITS sets their nickname to their WarEra name; the button
 * [OPT_IN_BUTTON] after linking turns it on as well. The role sync sets the nickname, so this only stores the choice.
 * Turning it off leaves the nickname as it is.
 */
class NicknameCommand(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
    private val languages: Languages,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        val enabled = event.getOption(OPTION_MODE)?.asString == MODE_ON
        event.deferReply(true).queue()
        set(event, event.hook, event.user.id, enabled, event.member)
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.componentId != OPT_IN_BUTTON) return
        event.deferReply(true).queue()
        set(event, event.hook, event.user.id, enabled = true, event.member)
    }

    private fun set(
        interaction: Interaction,
        hook: InteractionHook,
        discordUserId: String,
        enabled: Boolean,
        member: Member?,
    ) {
        scope.launch {
            val language = languages.of(interaction)
            val message =
                try {
                    preferenceMessage(backend.setNicknamePreference(discordUserId, enabled), renamable(member), language)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Setting the nickname preference at the backend failed", e)
                    bitsUnavailableMessage(language)
                }
            hook.sendMessage(message).queue()
        }
    }

    /** The owner and members above the bot cannot be renamed by any bot. */
    private fun renamable(member: Member?): Boolean {
        val self = member?.guild?.selfMember ?: return true
        return self.hasPermission(Permission.NICKNAME_MANAGE) && self.canInteract(member)
    }

    companion object {
        const val NAME = "nickname"
        private const val OPTION_MODE = "mode"
        private const val MODE_ON = "on"
        private const val MODE_OFF = "off"
        const val OPT_IN_BUTTON = "nickname:on"

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Sets whether BITS uses your WarEra name as your nickname")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Legt fest, ob BITS deinen Nickname auf deinen WarEra-Namen setzt")
                .addOptions(
                    OptionData(OptionType.STRING, OPTION_MODE, "on: WarEra name as nickname, off: BITS leaves it alone", true)
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "on: WarEra-Name als Nickname, off: BITS lässt ihn in Ruhe")
                        .addChoice(MODE_ON, MODE_ON)
                        .addChoice(MODE_OFF, MODE_OFF),
                )

        fun optInButton(language: Language): Button =
            Button.secondary(OPT_IN_BUTTON, language.text("Nickname übernehmen", "Use as nickname"))

        internal fun preferenceMessage(
            response: NicknamePreferenceResponse,
            renamable: Boolean,
            language: Language,
        ): String {
            if (!response.enabled) {
                return language.text(
                    "BITS ändert deinen Nickname nicht mehr. Er bleibt, wie er ist; ändern kannst du ihn in Discord selbst.",
                    "BITS no longer changes your nickname. It stays as it is; you can change it yourself in Discord.",
                )
            }
            val start =
                response.wareraUsername?.let {
                    val name = MarkdownSanitizer.escape(it)
                    language.text(
                        "Ab jetzt trägst du deinen WarEra-Namen **$name** als Nickname. Ich setze ihn in den nächsten Minuten und passe " +
                            "ihn an, wenn du dich in WarEra umbenennst.",
                        "From now on, your WarEra name **$name** is your nickname. I will set it within the next few minutes and update " +
                            "it if you rename yourself in WarEra.",
                    )
                } ?: language.text(
                    "Gespeichert. Sobald du dich mit `/${VerifyCommand.NAME}` verknüpft hast, trägst du deinen WarEra-Namen als Nickname.",
                    "Saved. As soon as you have linked your account with `/${VerifyCommand.NAME}`, your WarEra name becomes your nickname.",
                )
            val hint =
                if (renamable) {
                    ""
                } else {
                    language.text(
                        "\n\nHinweis: Deinen Nickname kann ich gerade nicht ändern, weil du den Server besitzt, über meiner Rolle " +
                            "stehst oder mir die Berechtigung fehlt.",
                        "\n\nNote: I cannot change your nickname right now, because you own the server, are above my role, or I lack " +
                            "the permission.",
                    )
                }
            return start + hint +
                language.text(
                    "\n\nBITS speichert dafür deine Discord-ID, bis du `/$NAME $MODE_OFF` nutzt oder den Server verlässt.",
                    "\n\nFor this, BITS stores your Discord ID until you use `/$NAME $MODE_OFF` or leave the server.",
                )
        }
    }
}
