package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.shared.VoteResultDto
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import kotlin.math.roundToInt

/**
 * How the result of a vote looks in Discord: the total in the description, a field per party and the non-voters,
 * with the chart as image. What does not fit into one embed goes into further ones, one message each, since Discord
 * counts the length of all embeds of a message together.
 */
object VoteResultMessages {
    const val NO_PARTY = "Parteilos"

    /** The first embed shows the chart, uploaded with the message as [imageName]. */
    fun embeds(
        result: VoteResultDto,
        imageName: String,
    ): List<MessageEmbed> {
        val first =
            EmbedBuilder()
                .setTitle(cut("Ergebnis: ${escape(result.question)}", MessageEmbed.TITLE_MAX_LENGTH))
                .setColor(VoteMessages.OPEN_COLOR)
                .setImage("attachment://$imageName")
        val eligible = result.parties.sumOf { it.eligible }
        if (eligible == 0) {
            return listOf(first.setDescription("Niemand mit der Rolle und einem verknüpften WarEra-Account konnte abstimmen.").build())
        }
        val yes = result.parties.sumOf { it.yes }
        val no = result.parties.sumOf { it.no }
        val abstain = result.parties.sumOf { it.abstain }
        first.setDescription("**Gesamt:** " + counts(yes, no, abstain, eligible))

        val fields =
            result.parties.map { party ->
                MessageEmbed.Field(
                    cut(escape(party.name ?: NO_PARTY), MessageEmbed.TITLE_MAX_LENGTH),
                    counts(party.yes, party.no, party.abstain, party.eligible),
                    true,
                )
            } + nonVoterFields(result.nonVoters)

        val embeds = mutableListOf<MessageEmbed>()
        var embed = first
        fields.forEach { field ->
            val length = field.name.orEmpty().length + field.value.orEmpty().length
            if (embed.fields.size == MessageEmbed.MAX_FIELD_AMOUNT || embed.length() + length > MessageEmbed.EMBED_MAX_LENGTH_BOT) {
                embeds += embed.build()
                embed = EmbedBuilder().setColor(VoteMessages.OPEN_COLOR)
            }
            embed.addField(field)
        }
        return embeds + embed.build()
    }

    /** "5 Ja · 1 Nein · 0 Enthaltung", then "6 von 7 haben abgestimmt (86 %)" below. */
    private fun counts(
        yes: Int,
        no: Int,
        abstain: Int,
        eligible: Int,
    ): String {
        val voted = yes + no + abstain
        val percent = if (eligible == 0) 0 else (voted * 100.0 / eligible).roundToInt()
        return "$yes Ja · $no Nein · $abstain Enthaltung\n$voted von $eligible haben abgestimmt ($percent %)"
    }

    /** All names, comma-separated, in as many fields as they need. */
    private fun nonVoterFields(names: List<String>): List<MessageEmbed.Field> {
        if (names.isEmpty()) return listOf(MessageEmbed.Field("Nicht abgestimmt", "Alle haben abgestimmt.", false))
        val values = mutableListOf<String>()
        var value = StringBuilder()
        names.forEach { raw ->
            val name = cut(escape(raw), MessageEmbed.VALUE_MAX_LENGTH)
            if (value.isNotEmpty() && value.length + 2 + name.length > MessageEmbed.VALUE_MAX_LENGTH) {
                values += value.toString()
                value = StringBuilder()
            }
            if (value.isNotEmpty()) value.append(", ")
            value.append(name)
        }
        values += value.toString()
        return values.mapIndexed { index, text ->
            val title = if (index == 0) "Nicht abgestimmt (${names.size})" else "Nicht abgestimmt (Fortsetzung)"
            MessageEmbed.Field(title, text, false)
        }
    }

    /** Escapes everything Discord reads as markdown, and defuses mentions. */
    fun escape(text: String): String =
        text
            .replace(Regex("""([\\*_~`|<>#\-\[\]()])"""), """\\$1""")
            .replace("@", "@​")

    /** Cuts [text] to [max] characters with an ellipsis, never leaving a lone escaping backslash at the end. */
    private fun cut(
        text: String,
        max: Int,
    ): String = if (text.length <= max) text else text.take(max - 1).trimEnd('\\') + "…"
}
