package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.shared.ParticipationResponse
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteState
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import java.time.Duration

/** How a vote looks in Discord, and the IDs of its buttons. */
object VoteMessages {
    private const val BUTTON_PREFIX = "vote"

    /** Also the colour of the result. */
    const val OPEN_COLOR = 0x5865F2
    private const val CLOSED_COLOR = 0x80848E

    /** The vote with its participation, and while it is open a footer for the voters. */
    fun embed(
        vote: VoteDto,
        participation: ParticipationResponse?,
    ): MessageEmbed {
        val open = vote.state == VoteState.OPEN
        val title =
            when (vote.state) {
                VoteState.OPEN -> "Abstimmung"
                VoteState.COUNTING, VoteState.EVALUATED -> "Abstimmung beendet"
                VoteState.CANCELLED -> "Abstimmung abgebrochen"
            }
        val end =
            when (vote.state) {
                VoteState.OPEN -> {
                    val date = vote.endsAt?.let { "<t:${it / 1000}:f> (<t:${it / 1000}:R>)" }
                    val percent = vote.autoClosePercent
                    when {
                        percent == null -> date ?: "offen, bis sie jemand beendet"
                        date == null -> "Endet automatisch bei $percent % Beteiligung, sonst wenn sie jemand beendet"
                        else -> "$date\nEndet automatisch bei $percent % Beteiligung, falls die früher erreicht ist"
                    }
                }
                VoteState.COUNTING, VoteState.EVALUATED ->
                    if (vote.endedByParticipation) {
                        "automatisch beendet bei ${vote.autoClosePercent} % Beteiligung, die Auswertung erscheint in diesem Kanal"
                    } else {
                        "beendet, die Auswertung erscheint in diesem Kanal"
                    }
                VoteState.CANCELLED -> "abgebrochen, es gibt keine Auswertung"
            }
        return EmbedBuilder()
            .setTitle(title)
            .setColor(if (open) OPEN_COLOR else CLOSED_COLOR)
            .setDescription("**${MarkdownSanitizer.escape(vote.question)}**")
            .addField("Stimmberechtigt", "<@&${vote.roleId}> mit verknüpftem WarEra-Account", false)
            .addField("Ende", end, false)
            .apply {
                if (participation != null && vote.state != VoteState.CANCELLED) {
                    addField("Beteiligung", "${participation.voted} von ${participation.eligible} haben abgestimmt", false)
                }
                if (open) {
                    setFooter(
                        "Deine Stimme sieht nur du; du kannst sie bis zum Ende ändern. Ausgewertet wird nach WarEra-Partei, " +
                            "namentlich genannt werden nur, wer nicht abgestimmt hat.",
                    )
                }
            }.build()
    }

    /** The three buttons while the vote is open, none afterwards. */
    fun components(vote: VoteDto): List<ActionRow> {
        if (vote.state != VoteState.OPEN) return emptyList()
        return listOf(
            ActionRow.of(
                Button.success(buttonId(vote, VoteChoice.YES), label(VoteChoice.YES)),
                Button.danger(buttonId(vote, VoteChoice.NO), label(VoteChoice.NO)),
                Button.secondary(buttonId(vote, VoteChoice.ABSTAIN), label(VoteChoice.ABSTAIN)),
            ),
        )
    }

    fun label(choice: VoteChoice) =
        when (choice) {
            VoteChoice.YES -> "Ja"
            VoteChoice.NO -> "Nein"
            VoteChoice.ABSTAIN -> "Enthaltung"
        }

    /** `vote:<id>:<choice>:<role ID>`; the role lets the bot check the voter without asking the backend. */
    private fun buttonId(
        vote: VoteDto,
        choice: VoteChoice,
    ) = "$BUTTON_PREFIX:${vote.id}:$choice:${vote.roleId}"

    /** `null` for buttons that are not a vote's. */
    fun parseButton(componentId: String): VoteButton? {
        val parts = componentId.split(':')
        if (parts.size != 4 || parts[0] != BUTTON_PREFIX) return null
        val voteId = parts[1].toLongOrNull() ?: return null
        val choice = VoteChoice.entries.firstOrNull { it.name == parts[2] } ?: return null
        return VoteButton(voteId, choice, parts[3])
    }

    /**
     * Reads a duration such as `48h`, `3d` or `90m`; [OPEN_END] for a vote without end. `null` for anything else,
     * and for less than a minute or more than [MAX_DURATION].
     */
    fun parseDuration(text: String): VoteEnd? {
        val value = text.trim().lowercase()
        if (value == OPEN_END) return VoteEnd.Open
        val match = Regex("""(\d{1,5})\s*([mhd])""").matchEntire(value) ?: return null
        val amount = match.groupValues[1].toLong()
        val duration =
            when (match.groupValues[2]) {
                "m" -> Duration.ofMinutes(amount)
                "h" -> Duration.ofHours(amount)
                else -> Duration.ofDays(amount)
            }
        if (duration < Duration.ofMinutes(1) || duration > MAX_DURATION) return null
        return VoteEnd.After(duration)
    }

    const val OPEN_END = "offen"
    const val DEFAULT_DURATION = "48h"
    val MAX_DURATION: Duration = Duration.ofDays(90)
}

data class VoteButton(
    val voteId: Long,
    val choice: VoteChoice,
    val roleId: String,
)

sealed interface VoteEnd {
    data object Open : VoteEnd

    data class After(
        val duration: Duration,
    ) : VoteEnd
}
