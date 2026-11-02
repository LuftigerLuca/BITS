package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.bot.backend.VoteBackend
import eu.luftiger.bits.bot.channels.ChannelReporter
import eu.luftiger.bits.bot.commands.bitsUnavailableMessage
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.CloseVoteResponse
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.StartVoteRequest
import eu.luftiger.bits.shared.StartVoteResponse
import eu.luftiger.bits.shared.VoteActor
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteThreshold
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
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * `/vote start|end|cancel` for admins and the roles the dashboard allows; the backend decides with the
 * member's roles whether they may. The vote itself is posted once the backend's event arrives, see [VoteEvents].
 */
class VoteCommand(
    private val backend: VoteBackend,
    private val scope: CoroutineScope,
    private val languages: Languages,
    private val clock: Clock = Clock.systemUTC(),
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != NAME) return
        event.deferReply(true).queue()
        val member = event.member
        scope.launch {
            val language = languages.of(event)
            val message =
                try {
                    when {
                        member == null -> language.text("Abstimmungen gibt es nur auf dem Server.", "Votes only exist on the server.")
                        event.subcommandName == START -> start(event, member, language)
                        else -> close(event, member, cancel = event.subcommandName == CANCEL, language)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Vote command failed at the backend", e)
                    bitsUnavailableMessage(language)
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
        language: Language,
    ): String {
        val question = event.getOption(OPTION_QUESTION)?.asString.orEmpty()
        val role =
            event.getOption(OPTION_ROLE)?.asRole
                ?: return language.text("Wähle die Rolle, die abstimmen darf.", "Choose the role that may vote.")
        val channel =
            event.getOption(OPTION_CHANNEL)?.asChannel as? GuildChannel
                ?: return language.text("Wähle einen Kanal.", "Choose a channel.")
        val endText = event.getOption(OPTION_END)?.asString ?: VoteMessages.DEFAULT_DURATION
        if (role.isPublicRole) return language.text("Wähle eine Rolle, nicht @everyone.", "Choose a role, not @everyone.")
        if (!member.guild.selfMember.hasPermission(channel, ChannelReporter.POST_PERMISSIONS)) {
            return language.text(
                "In ${channel.asMention} kann ich keine Abstimmung posten. Ich brauche dort die Rechte, Nachrichten, Embeds und " +
                    "Dateien zu senden und den Verlauf zu lesen.",
                "I cannot post a vote in ${channel.asMention}. I need the permissions to send messages, embeds and files there and " +
                    "to read the history.",
            )
        }
        val endsAt =
            when (val end = VoteMessages.parseDuration(endText)) {
                null ->
                    return language.text(
                        "Das Ende verstehe ich nicht. Gib eine Dauer wie `48h`, `3d` oder `90m` an, höchstens 90 Tage, oder " +
                            "`${VoteMessages.OPEN_END}` für eine Abstimmung ohne Enddatum.",
                        "I do not understand the end. Give a duration like `48h`, `3d` or `90m`, at most 90 days, or " +
                            "`${VoteMessages.OPEN_END}` for a vote without an end date.",
                    )
                VoteEnd.Open -> null
                is VoteEnd.After -> clock.instant().plus(end.duration).toEpochMilli()
            }
        val thresholdText = event.getOption(OPTION_THRESHOLD)?.asString
        val thresholdChoiceText = event.getOption(OPTION_THRESHOLD_CHOICE)?.asString
        // Discord keeps the options within their range and choices already; this is for an outdated command.
        val percent = thresholdText?.toIntOrNull()
        val choice = thresholdChoiceText?.let { text -> THRESHOLD_CHOICES.firstOrNull { it.name == text } }
        if (thresholdText != null && (percent == null || percent !in 1..99)) {
            return language.text(
                "Die Schwelle gibst du in Prozent von 1 bis 99 an, z. B. `50` für mehr als die Hälfte.",
                "Give the threshold in percent from 1 to 99, e.g. `50` for more than half.",
            )
        }
        if (thresholdChoiceText != null && choice == null) {
            return language.text("Die Schwelle gilt für `Ja` oder `Nein`.", "The threshold applies to `Yes` or `No`.")
        }
        if (percent == null && choice != null) {
            return language.text(
                "Gib zur Stimme auch die Schwelle an, z. B. `$OPTION_THRESHOLD:50`, oder lass beides weg.",
                "Give the threshold along with the choice, e.g. `$OPTION_THRESHOLD:50`, or leave out both.",
            )
        }
        val threshold = percent?.let { VoteThreshold(it, choice ?: VoteChoice.YES) }
        val request = StartVoteRequest(actor(member), question, role.id, channel.id, endsAt, threshold)
        return when (backend.startVote(request)) {
            is StartVoteResponse.Started ->
                language.text(
                    "Die Abstimmung ist gestartet und erscheint gleich in ${channel.asMention}. Beenden oder abbrechen kannst du sie " +
                        "mit `/$NAME $END` oder `/$NAME $CANCEL` oder im Dashboard.",
                    "The vote has started and will appear in ${channel.asMention} shortly. You can end or cancel it with " +
                        "`/$NAME $END` or `/$NAME $CANCEL` or in the dashboard.",
                )
            StartVoteResponse.Forbidden -> forbiddenMessage(language)
            StartVoteResponse.ChannelNotPostable ->
                language.text(
                    "In ${channel.asMention} kann ich laut meinem letzten Stand nicht posten. Prüfe meine Rechte dort und versuche es " +
                        "in ein paar Sekunden noch einmal.",
                    "As far as I know, I cannot post in ${channel.asMention}. Check my permissions there and try again in a few seconds.",
                )
        }
    }

    private suspend fun close(
        event: SlashCommandInteractionEvent,
        member: Member,
        cancel: Boolean,
        language: Language,
    ): String {
        val voteId =
            event.getOption(OPTION_VOTE)?.asString?.toLongOrNull()
                ?: return language.text("Wähle eine laufende Abstimmung aus der Liste.", "Choose a running vote from the list.")
        return when (backend.closeVote(voteId, actor(member), cancel)) {
            CloseVoteResponse.Closed ->
                if (cancel) {
                    language.text(
                        "Die Abstimmung ist abgebrochen. Es gibt keine Auswertung, die abgegebenen Stimmen sind gelöscht.",
                        "The vote is cancelled. There is no result, and the votes cast are deleted.",
                    )
                } else {
                    language.text(
                        "Die Abstimmung ist beendet. Die Auswertung erscheint in ein paar Minuten in ihrem Kanal.",
                        "The vote has ended. The result will appear in its channel in a few minutes.",
                    )
                }
            CloseVoteResponse.Forbidden -> forbiddenMessage(language)
            CloseVoteResponse.NotOpen -> language.text("Diese Abstimmung läuft nicht mehr.", "This vote is no longer running.")
        }
    }

    companion object {
        const val NAME = "vote"
        const val START = "start"
        private const val END = "end"
        private const val CANCEL = "cancel"
        private const val OPTION_QUESTION = "question"
        private const val OPTION_ROLE = "role"
        private const val OPTION_CHANNEL = "channel"
        private const val OPTION_END = "end"
        private const val OPTION_THRESHOLD = "threshold"
        private const val OPTION_THRESHOLD_CHOICE = "choice"
        private const val OPTION_VOTE = "vote"
        private const val AUTOCOMPLETE_TIMEOUT_MILLIS = 2_500L

        /** Like the backend's limit. */
        private const val MAX_QUESTION_LENGTH = 500

        /** Abstaining decides nothing, so a threshold is for Yes or No only. */
        private val THRESHOLD_CHOICES = listOf(VoteChoice.YES, VoteChoice.NO)

        internal fun forbiddenMessage(language: Language): String =
            language.text(
                "Abstimmungen verwalten dürfen nur Admins und Mitglieder der Rollen, die dafür im Dashboard freigegeben sind.",
                "Only admins and members of the roles allowed in the dashboard may manage votes.",
            )

        private fun runningVote() =
            OptionData(OptionType.STRING, OPTION_VOTE, "The running vote", true, true)
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Die laufende Abstimmung")

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Starts, ends or cancels a yes/no vote")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Startet, beendet oder bricht eine Ja/Nein-Abstimmung ab")
                .addSubcommands(
                    SubcommandData(START, "Starts a vote with the buttons Ja, Nein and Enthaltung")
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Startet eine Abstimmung mit den Buttons Ja, Nein und Enthaltung")
                        .addOptions(
                            OptionData(OptionType.STRING, OPTION_QUESTION, "What the vote is about", true)
                                .setDescriptionLocalization(DiscordLocale.GERMAN, "Worüber abgestimmt wird")
                                .setMaxLength(MAX_QUESTION_LENGTH),
                            OptionData(OptionType.ROLE, OPTION_ROLE, "Who may vote", true)
                                .setDescriptionLocalization(DiscordLocale.GERMAN, "Wer abstimmen darf"),
                            OptionData(OptionType.CHANNEL, OPTION_CHANNEL, "Where the vote and its result appear", true)
                                .setDescriptionLocalization(DiscordLocale.GERMAN, "Wo die Abstimmung und ihr Ergebnis erscheinen")
                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                            OptionData(
                                OptionType.STRING,
                                OPTION_END,
                                "Duration like 48h, 3d or 90m, or “${VoteMessages.OPEN_END}” for no end date; default 48h",
                                false,
                            ).setDescriptionLocalization(
                                DiscordLocale.GERMAN,
                                "Dauer wie 48h, 3d oder 90m, oder „${VoteMessages.OPEN_END}“ für kein Enddatum; Standard 48h",
                            ),
                            OptionData(
                                OptionType.INTEGER,
                                OPTION_THRESHOLD,
                                "Ends early once more than this percentage of all voters vote “choice”",
                                false,
                            ).setDescriptionLocalization(
                                DiscordLocale.GERMAN,
                                "Endet vorzeitig, sobald mehr als so viel Prozent aller Stimmberechtigten mit „choice“ stimmen",
                            ).setRequiredRange(1, 99),
                            OptionData(
                                OptionType.STRING,
                                OPTION_THRESHOLD_CHOICE,
                                "Which vote the threshold applies to; default Yes",
                                false,
                            ).setDescriptionLocalization(DiscordLocale.GERMAN, "Für welche Stimme die Schwelle gilt; Standard Ja")
                                .addChoices(
                                    Command
                                        .Choice(VoteMessages.label(VoteChoice.YES, Language.ENGLISH), VoteChoice.YES.name)
                                        .setNameLocalization(DiscordLocale.GERMAN, VoteMessages.label(VoteChoice.YES)),
                                    Command
                                        .Choice(VoteMessages.label(VoteChoice.NO, Language.ENGLISH), VoteChoice.NO.name)
                                        .setNameLocalization(DiscordLocale.GERMAN, VoteMessages.label(VoteChoice.NO)),
                                ),
                        ),
                    SubcommandData(END, "Ends a vote early; the result follows")
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Beendet eine Abstimmung vorzeitig; die Auswertung folgt")
                        .addOptions(runningVote()),
                    SubcommandData(CANCEL, "Cancels a vote without a result")
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Bricht eine Abstimmung ohne Auswertung ab")
                        .addOptions(runningVote()),
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
