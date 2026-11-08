package eu.luftiger.bits.bot.reprimands

import eu.luftiger.bits.bot.commands.bitsUnavailableMessage
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.ReprimandMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.slf4j.LoggerFactory

/** `/reprimand <member> <reason>`; the reply only the member who used it sees, the reprimand itself everybody. */
class ReprimandCommand(
    private val reprimands: Reprimands,
    private val scope: CoroutineScope,
    private val languages: Languages,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        event.deferReply(true).queue()
        val actor = event.member
        val target = event.getOption(OPTION_MEMBER)?.asMember
        val reason = event.getOption(OPTION_REASON)?.asString.orEmpty()
        scope.launch {
            val language = languages.of(event)
            val message =
                try {
                    if (actor == null || target == null) {
                        Reprimands.notMemberMessage(language)
                    } else {
                        reprimands.reprimand(
                            member(actor),
                            member(target),
                            target.asMention,
                            reason,
                            { embed ->
                                event.channel
                                    .sendMessageEmbeds(embed)
                                    .submit()
                                    .await()
                            },
                            language,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("/{} failed at the backend", NAME, e)
                    bitsUnavailableMessage(language)
                }
            event.hook.sendMessage(message).queue()
        }
    }

    companion object {
        const val NAME = "reprimand"
        private const val OPTION_MEMBER = "member"
        private const val OPTION_REASON = "reason"
        private const val MAX_REASON_LENGTH = 500

        /** The member as the backend needs them to decide about a reprimand. */
        internal fun member(member: Member) =
            ReprimandMember(
                discordUserId = member.id,
                name = member.effectiveName,
                avatarUrl = member.effectiveAvatarUrl,
                roleIds = member.roles.map { it.id },
                administrator = member.hasPermission(Permission.ADMINISTRATOR),
                bot = member.user.isBot,
            )

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Reprimands a member, who is timed out for it")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Rügt ein Mitglied, das dafür einen Timeout bekommt")
                .addOptions(
                    OptionData(OptionType.USER, OPTION_MEMBER, "The member to reprimand", true)
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Das Mitglied, das gerügt wird"),
                    OptionData(OptionType.STRING, OPTION_REASON, "Why; everybody in the channel sees it", true)
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Warum; alle im Kanal sehen es")
                        .setMaxLength(MAX_REASON_LENGTH),
                )
    }
}
