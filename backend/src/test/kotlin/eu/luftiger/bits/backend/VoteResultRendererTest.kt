package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.internal.VoteResult
import eu.luftiger.bits.backend.vote.internal.VoteResultRenderer
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoteResultRendererTest {
    private val result =
        VoteResult(
            question = "Soll der *Kongress* die Steuern senken?",
            parties =
                listOf(
                    PartyResult(1, 0, "Deutschland Partei", yes = 10, no = 3, abstain = 1, eligible = 20),
                    PartyResult(1, 1, "SKP_Katzen", yes = 0, no = 0, abstain = 0, eligible = 2),
                    PartyResult(1, 2, null, yes = 1, no = 1, abstain = 0, eligible = 4),
                ),
            nonVoters = (1..80).map { "Mitglied Nummer $it" },
        )

    @Test
    fun `markdown escapes names, plain text does not`() {
        val markdown = VoteResultRenderer.text(result, markdown = true)
        val plain = VoteResultRenderer.text(result, markdown = false)

        assertTrue(markdown.startsWith("**Ergebnis:** Soll der \\*Kongress\\* die Steuern senken?\n"), markdown)
        assertTrue("SKP\\_Katzen: 0 Ja · 0 Nein · 0 Enthaltung · 0 von 2 haben abgestimmt (0 %)" in markdown, markdown)
        assertTrue(plain.startsWith("Ergebnis: Soll der *Kongress* die Steuern senken?\n"), plain)
        assertTrue("Gesamt: 11 Ja · 4 Nein · 1 Enthaltung · 16 von 26 haben abgestimmt (62 %)" in plain, plain)
        assertTrue("Parteilos: 1 Ja · 1 Nein · 0 Enthaltung · 2 von 4 haben abgestimmt (50 %)" in plain, plain)
    }

    @Test
    fun `the text lists every non-voter`() {
        assertTrue(VoteResultRenderer.text(result, markdown = false).endsWith("Mitglied Nummer 79, Mitglied Nummer 80"))
    }

    @Test
    fun `mentions in names are defused`() {
        assertEquals("@​everyone", VoteResultRenderer.escapeMarkdown("@everyone"))
    }

    @Test
    fun `a vote nobody could join says so`() {
        val empty = VoteResult("Frage?", emptyList(), emptyList())

        assertTrue(
            VoteResultRenderer
                .text(
                    empty,
                    markdown = false,
                ).endsWith("Niemand mit der Rolle und einem verknüpften WarEra-Account konnte abstimmen."),
        )
        assertEquals(1100, ImageIO.read(ByteArrayInputStream(VoteResultRenderer.png(empty))).width)
    }

    @Test
    fun `the image cuts the list of non-voters short`() {
        val image = ImageIO.read(ByteArrayInputStream(VoteResultRenderer.png(result)))
        System.getenv("VOTE_IMAGE_OUT")?.let { java.io.File(it).writeBytes(VoteResultRenderer.png(result)) }

        assertEquals(1100, image.width)
        assertTrue(image.height < 700, "height ${image.height}")
    }
}
