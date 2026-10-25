package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
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
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory

/**
 * `/nickname an|aus` lets members choose whether BITS sets their nickname to their WarEra name; the button
 * [OPT_IN_BUTTON] after linking turns it on as well. The role sync sets the nickname, so this only stores the choice.
 * Turning it off leaves the nickname as it is.
 */
class NicknameCommand(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        val enabled = event.getOption(OPTION_MODE)?.asString == MODE_ON
        event.deferReply(true).queue()
        set(event.hook, event.user.id, enabled, event.member)
    }

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.componentId != OPT_IN_BUTTON) return
        event.deferReply(true).queue()
        set(event.hook, event.user.id, enabled = true, event.member)
    }

    private fun set(
        hook: InteractionHook,
        discordUserId: String,
        enabled: Boolean,
        member: Member?,
    ) {
        scope.launch {
            val message =
                try {
                    preferenceMessage(backend.setNicknamePreference(discordUserId, enabled), renamable(member))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Setting the nickname preference at the backend failed", e)
                    BITS_UNAVAILABLE_MESSAGE
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
        private const val OPTION_MODE = "modus"
        private const val MODE_ON = "an"
        private const val MODE_OFF = "aus"
        const val OPT_IN_BUTTON = "nickname:on"

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Legt fest, ob BITS deinen Nickname auf deinen WarEra-Namen setzt")
                .addOptions(
                    OptionData(OptionType.STRING, OPTION_MODE, "an: WarEra-Name als Nickname, aus: BITS lässt ihn in Ruhe", true)
                        .addChoice(MODE_ON, MODE_ON)
                        .addChoice(MODE_OFF, MODE_OFF),
                )

        val optInButton: Button = Button.secondary(OPT_IN_BUTTON, "Nickname übernehmen")

        internal fun preferenceMessage(
            response: NicknamePreferenceResponse,
            renamable: Boolean,
        ): String {
            if (!response.enabled) {
                return "BITS ändert deinen Nickname nicht mehr. Er bleibt, wie er ist; ändern kannst du ihn in Discord selbst."
            }
            val start =
                response.wareraUsername?.let {
                    "Ab jetzt trägst du deinen WarEra-Namen **${MarkdownSanitizer.escape(it)}** als Nickname. Ich setze ihn in den " +
                        "nächsten Minuten und passe ihn an, wenn du dich in WarEra umbenennst."
                } ?: "Gespeichert. Sobald du dich mit `/${VerifyCommand.NAME}` verknüpft hast, trägst du deinen WarEra-Namen als Nickname."
            val hint =
                if (renamable) {
                    ""
                } else {
                    "\n\nHinweis: Deinen Nickname kann ich gerade nicht ändern, weil du den Server besitzt, über meiner Rolle stehst " +
                        "oder mir die Berechtigung fehlt."
                }
            return start + hint + "\n\nBITS speichert dafür deine Discord-ID, bis du `/$NAME $MODE_OFF` nutzt oder den Server " +
                "verlässt."
        }
    }
}
