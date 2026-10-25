package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.bot.backend.VoteBackend
import eu.luftiger.bits.bot.channels.ChannelReporter
import eu.luftiger.bits.bot.commands.BITS_UNAVAILABLE_MESSAGE
import eu.luftiger.bits.shared.CloseVoteResponse
import eu.luftiger.bits.shared.StartVoteRequest
import eu.luftiger.bits.shared.StartVoteResponse
import eu.luftiger.bits.shared.VoteActor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * `/abstimmung starten|beenden|abbrechen` for admins and the roles the dashboard allows; the backend decides with the
 * member's roles whether they may. The vote itself is posted once the backend's event arrives, see [VoteEvents].
 */
class VoteCommand(
    private val backend: VoteBackend,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        event.deferReply(true).queue()
        val member = event.member
        scope.launch {
            val message =
                try {
                    when {
                        member == null -> "Abstimmungen gibt es nur auf dem Server."
                        event.subcommandName == START -> start(event, member)
                        else -> close(event, member, cancel = event.subcommandName == CANCEL)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Vote command failed at the backend", e)
                    BITS_UNAVAILABLE_MESSAGE
                }
            event.hook.sendMessage(message).queue()
        }
    }

    /** Offers the open votes by their question. */
    override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
        if (event.name != NAME || event.focusedOption.name != OPTION_VOTE) return
        val typed = event.focusedOption.value.trim()
        scope.launch {
            val choices =
                try {
                    // Discord waits three seconds for the choices.
                    withTimeout(AUTOCOMPLETE_TIMEOUT_MILLIS) { backend.openVotes() }
                        .filter { typed.isEmpty() || it.question.contains(typed, ignoreCase = true) }
                        .take(OptionData.MAX_CHOICES)
                        .map { Command.Choice(it.question.take(OptionData.MAX_CHOICE_NAME_LENGTH), it.id.toString()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Open votes could not be loaded for the autocompletion", e)
                    emptyList()
                }
            event.replyChoices(choices).queue()
        }
    }

    private suspend fun start(
        event: SlashCommandInteractionEvent,
        member: Member,
    ): String {
        val question = event.getOption(OPTION_QUESTION)?.asString.orEmpty()
        val role = event.getOption(OPTION_ROLE)?.asRole ?: return "Wähle die Rolle, die abstimmen darf."
        val channel = event.getOption(OPTION_CHANNEL)?.asChannel as? GuildChannel ?: return "Wähle einen Kanal."
        val endText = event.getOption(OPTION_END)?.asString ?: VoteMessages.DEFAULT_DURATION
        if (role.isPublicRole) return "Wähle eine Rolle, nicht @everyone."
        if (!member.guild.selfMember.hasPermission(channel, ChannelReporter.POST_PERMISSIONS)) {
            return "In ${channel.asMention} kann ich keine Abstimmung posten. Ich brauche dort die Rechte, Nachrichten, Embeds und " +
                "Dateien zu senden und den Verlauf zu lesen."
        }
        val endsAt =
            when (val end = VoteMessages.parseDuration(endText)) {
                null ->
                    return "Das Ende verstehe ich nicht. Gib eine Dauer wie `48h`, `3d` oder `90m` an, höchstens 90 Tage, oder " +
                        "`${VoteMessages.OPEN_END}` für eine Abstimmung ohne Enddatum."
                VoteEnd.Open -> null
                is VoteEnd.After -> clock.instant().plus(end.duration).toEpochMilli()
            }
        val participationText = event.getOption(OPTION_PARTICIPATION)?.asString
        // Discord keeps the option within the range already; this is for an outdated command.
        val autoClosePercent = participationText?.toIntOrNull()
        if (participationText != null && (autoClosePercent == null || autoClosePercent !in 1..100)) {
            return "Die Beteiligung gibst du in Prozent von 1 bis 100 an, z. B. `60`."
        }
        val request = StartVoteRequest(actor(member), question, role.id, channel.id, endsAt, autoClosePercent)
        return when (backend.startVote(request)) {
            is StartVoteResponse.Started ->
                "Die Abstimmung ist gestartet und erscheint gleich in ${channel.asMention}. Beenden oder abbrechen kannst du sie " +
                    "mit `/$NAME $END` oder `/$NAME $CANCEL` oder im Dashboard."
            StartVoteResponse.Forbidden -> FORBIDDEN_MESSAGE
            StartVoteResponse.ChannelNotPostable ->
                "In ${channel.asMention} kann ich laut meinem letzten Stand nicht posten. Prüfe meine Rechte dort und versuche es " +
                    "in ein paar Sekunden noch einmal."
        }
    }

    private suspend fun close(
        event: SlashCommandInteractionEvent,
        member: Member,
        cancel: Boolean,
    ): String {
        val voteId = event.getOption(OPTION_VOTE)?.asString?.toLongOrNull() ?: return "Wähle eine laufende Abstimmung aus der Liste."
        return when (backend.closeVote(voteId, actor(member), cancel)) {
            CloseVoteResponse.Closed ->
                if (cancel) {
                    "Die Abstimmung ist abgebrochen. Es gibt keine Auswertung, die abgegebenen Stimmen sind gelöscht."
                } else {
                    "Die Abstimmung ist beendet. Die Auswertung erscheint in ein paar Minuten in ihrem Kanal."
                }
            CloseVoteResponse.Forbidden -> FORBIDDEN_MESSAGE
            CloseVoteResponse.NotOpen -> "Diese Abstimmung läuft nicht mehr."
        }
    }

    companion object {
        const val NAME = "abstimmung"
        private const val START = "starten"
        private const val END = "beenden"
        private const val CANCEL = "abbrechen"
        private const val OPTION_QUESTION = "frage"
        private const val OPTION_ROLE = "rolle"
        private const val OPTION_CHANNEL = "kanal"
        private const val OPTION_END = "ende"
        private const val OPTION_PARTICIPATION = "beteiligung"
        private const val OPTION_VOTE = "abstimmung"
        private const val AUTOCOMPLETE_TIMEOUT_MILLIS = 2_500L

        /** Like the backend's limit. */
        private const val MAX_QUESTION_LENGTH = 500

        internal const val FORBIDDEN_MESSAGE =
            "Abstimmungen verwalten dürfen nur Admins und Mitglieder der Rollen, die dafür im Dashboard freigegeben sind."

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Startet, beendet oder bricht eine Ja/Nein-Abstimmung ab")
                .addSubcommands(
                    SubcommandData(START, "Startet eine Abstimmung mit den Buttons Ja, Nein und Enthaltung")
                        .addOptions(
                            OptionData(OptionType.STRING, OPTION_QUESTION, "Worüber abgestimmt wird", true)
                                .setMaxLength(MAX_QUESTION_LENGTH),
                            OptionData(OptionType.ROLE, OPTION_ROLE, "Wer abstimmen darf", true),
                            OptionData(OptionType.CHANNEL, OPTION_CHANNEL, "Wo die Abstimmung und ihr Ergebnis erscheinen", true)
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                            OptionData(
                                OptionType.STRING,
                                OPTION_END,
                                "Dauer wie 48h, 3d oder 90m, oder „${VoteMessages.OPEN_END}“ für kein Enddatum; Standard 48h",
                                false,
                            ),
                            OptionData(
                                OptionType.INTEGER,
                                OPTION_PARTICIPATION,
                                "Beteiligung in Prozent, bei der sie vorzeitig endet, z. B. 60",
                                false,
                            ).setRequiredRange(1, 100),
                        ),
                    SubcommandData(END, "Beendet eine Abstimmung vorzeitig; die Auswertung folgt")
                        .addOptions(OptionData(OptionType.STRING, OPTION_VOTE, "Die laufende Abstimmung", true, true)),
                    SubcommandData(CANCEL, "Bricht eine Abstimmung ohne Auswertung ab")
                        .addOptions(OptionData(OptionType.STRING, OPTION_VOTE, "Die laufende Abstimmung", true, true)),
                )

        /** The member as the backend needs them to decide whether they may manage votes. */
        internal fun actor(member: Member) =
            VoteActor(
                discordUserId = member.id,
                name = member.effectiveName,
                avatarUrl = member.effectiveAvatarUrl,
                roleIds = member.roles.map { it.id },
                administrator = member.hasPermission(Permission.ADMINISTRATOR),
            )
    }
}
