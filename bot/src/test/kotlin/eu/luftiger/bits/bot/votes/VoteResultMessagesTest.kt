package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.shared.PartyResultDto
import eu.luftiger.bits.shared.VoteResultDto
import net.dv8tion.jda.api.entities.MessageEmbed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoteResultMessagesTest {
    private val result =
        VoteResultDto(
            question = "Soll der *Kongress* die Steuern senken?",
            parties =
                listOf(
                    PartyResultDto("Deutschland Partei", yes = 5, no = 1, abstain = 0, eligible = 7),
                    PartyResultDto("SKP_Katzen", yes = 0, no = 0, abstain = 0, eligible = 2),
                    PartyResultDto(null, yes = 1, no = 0, abstain = 1, eligible = 4),
                ),
            nonVoters = listOf("@everyone", "<@123>", "Clara"),
            image = "",
            posted = false,
        )

    @Test
    fun `the result shows the total, a field per party and the non-voters, with the chart`() {
        val embed = VoteResultMessages.embeds(result, "abstimmung-7.png").single()

        assertEquals("Ergebnis: Soll der \\*Kongress\\* die Steuern senken?", embed.title)
        assertEquals("**Gesamt:** 6 Ja · 1 Nein · 1 Enthaltung\n8 von 13 haben abgestimmt (62 %)", embed.description)
        assertEquals("attachment://abstimmung-7.png", embed.image?.url)
        assertEquals(VoteMessages.OPEN_COLOR, embed.colorRaw)
        assertEquals(
            listOf(
                Triple("Deutschland Partei", "5 Ja · 1 Nein · 0 Enthaltung\n6 von 7 haben abgestimmt (86 %)", true),
                Triple("SKP\\_Katzen", "0 Ja · 0 Nein · 0 Enthaltung\n0 von 2 haben abgestimmt (0 %)", true),
                Triple("Parteilos", "1 Ja · 0 Nein · 1 Enthaltung\n2 von 4 haben abgestimmt (50 %)", true),
                Triple("Nicht abgestimmt (3)", "@\u200Beveryone, \\<@\u200B123\\>, Clara", false),
            ),
            embed.fields.map { Triple(it.name, it.value, it.isInline) },
        )
    }

    @Test
    fun `a vote everybody joined says so`() {
        val embed = VoteResultMessages.embeds(result.copy(nonVoters = emptyList()), "a.png").single()

        assertEquals("Alle haben abgestimmt.", embed.fields.last().value)
    }

    @Test
    fun `a vote nobody could join shows the hint without fields`() {
        val embed = VoteResultMessages.embeds(result.copy(parties = emptyList(), nonVoters = emptyList()), "a.png").single()

        assertEquals("Niemand mit der Rolle und einem verknüpften WarEra-Account konnte abstimmen.", embed.description)
        assertTrue(embed.fields.isEmpty())
        assertEquals("attachment://a.png", embed.image?.url)
    }

    @Test
    fun `all of 300 non-voters are named, in embeds Discord takes`() {
        val names = (1..300).map { "Mitglied_mit_langem_Namen_${it.toString().padStart(3, '0')}" }
        val embeds = VoteResultMessages.embeds(result.copy(nonVoters = names), "a.png")

        assertTrue(embeds.size > 1, "${embeds.size} embeds")
        embeds.forEach { assertFits(it) }
        val fields = embeds.flatMap { it.fields }.filterNot { it.isInline }
        assertEquals("Nicht abgestimmt (300)", fields.first().name)
        assertTrue(fields.drop(1).all { it.name == "Nicht abgestimmt (Fortsetzung)" })
        assertEquals(names.map(VoteResultMessages::escape), fields.flatMap { it.value!!.split(", ") })
        // Only the first embed has the title and the chart.
        assertTrue(embeds.drop(1).all { it.title == null && it.image == null })
    }

    @Test
    fun `parties beyond 25 fields go into a further embed`() {
        val parties = (1..30).map { PartyResultDto("Partei $it", yes = 1, no = 0, abstain = 0, eligible = 1) }
        val embeds = VoteResultMessages.embeds(result.copy(parties = parties), "a.png")

        assertEquals(listOf(25, 6), embeds.map { it.fields.size })
        assertEquals((1..30).map { "Partei $it" } + "Nicht abgestimmt (3)", embeds.flatMap { it.fields }.map { it.name })
    }

    @Test
    fun `a long question is cut to fit the title`() {
        val embed = VoteResultMessages.embeds(result.copy(question = "Frage ".repeat(100)), "a.png").first()

        assertEquals(MessageEmbed.TITLE_MAX_LENGTH, embed.title!!.length)
        assertTrue(embed.title!!.endsWith("…"))
    }

    private fun assertFits(embed: MessageEmbed) {
        assertTrue(embed.length <= MessageEmbed.EMBED_MAX_LENGTH_BOT, "length ${embed.length}")
        assertTrue(embed.fields.size <= MessageEmbed.MAX_FIELD_AMOUNT)
        assertTrue(embed.fields.all { it.value!!.length <= MessageEmbed.VALUE_MAX_LENGTH })
    }
}
