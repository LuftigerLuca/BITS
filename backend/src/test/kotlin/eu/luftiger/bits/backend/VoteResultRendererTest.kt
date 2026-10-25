package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.internal.VoteBackground
import eu.luftiger.bits.backend.vote.internal.VotePalette
import eu.luftiger.bits.backend.vote.internal.VoteResult
import eu.luftiger.bits.backend.vote.internal.VoteResultRenderer
import eu.luftiger.bits.backend.vote.internal.VoteResultStyle
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
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
        val image = read(VoteResultRenderer.png(result))
        System.getenv("VOTE_IMAGE_OUT")?.let { java.io.File(it).writeBytes(VoteResultRenderer.png(result)) }

        assertEquals(1100, image.width)
        assertTrue(image.height < 1300, "height ${image.height}")
    }

    @Test
    fun `each party stacks in its colour, those in no party in grey`() {
        val colors = VoteResultRenderer.colors(result.parties)
        val image = read(VoteResultRenderer.png(result))

        assertEquals(listOf(VotePalette.color(0), VotePalette.color(1), VotePalette.noParty), colors)
        // More than the swatch in the legend: the party with votes has a segment in the columns.
        assertTrue(pixels(image, colors[0]) > 2_000)
        // SKP_Katzen has no votes, so only its swatch shows.
        assertTrue(pixels(image, colors[1]) in 1..SWATCH_AREA)
    }

    @Test
    fun `colours an admin picked replace the palette's, and the other parties keep theirs`() {
        val parties =
            listOf(
                PartyResult(1, 0, "A", yes = 1, no = 0, abstain = 0, eligible = 1, partyId = "p-a"),
                PartyResult(1, 1, "B", yes = 1, no = 0, abstain = 0, eligible = 1, partyId = "p-b"),
                PartyResult(1, 2, null, yes = 1, no = 0, abstain = 0, eligible = 1),
            )
        val style = VoteResultStyle(partyColors = mapOf("p-a" to Color.RED), noPartyColor = Color.GREEN)

        assertEquals(listOf(Color.RED, VotePalette.color(1), Color.GREEN), VoteResultRenderer.colors(parties, style))
    }

    @Test
    fun `21 parties are told apart by their colour, and the legend grows with them`() {
        val colors = (0 until 21).map(VotePalette::color) + VotePalette.noParty
        colors.forEachIndexed { i, a ->
            colors.drop(i + 1).forEach { b -> assertTrue(deltaE(a, b) > 7, "$a and $b are too close") }
        }

        val many =
            VoteResult(
                "Frage?",
                (0 until 21).map { PartyResult(1, it, "Partei $it", yes = 1, no = 1, abstain = 0, eligible = 3) },
                emptyList(),
            )
        val few = VoteResult("Frage?", many.parties.take(1), emptyList())
        val image = read(VoteResultRenderer.png(many))
        assertTrue(image.height > read(VoteResultRenderer.png(few)).height + 300)
        colors.dropLast(1).forEach { assertTrue(pixels(image, it) > SWATCH_AREA, "$it is missing in the columns") }
    }

    @Test
    fun `a vote without votes draws no segments`() {
        val silent = VoteResult("Frage?", listOf(PartyResult(1, 0, "Partei", yes = 0, no = 0, abstain = 0, eligible = 3)), listOf("Anna"))

        assertTrue(pixels(read(VoteResultRenderer.png(silent)), VotePalette.color(0)) in 1..SWATCH_AREA)
    }

    @Test
    fun `the background covers the image and is darkened`() {
        val white =
            BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().run {
                    color = Color.WHITE
                    fillRect(0, 0, 400, 100)
                    dispose()
                }
            }
        val covered = VoteBackground.cover(white, 300, 300)

        assertEquals(300 to 300, covered.width to covered.height)
        val brightness = Color(covered.getRGB(150, 150)).red
        assertTrue(brightness in 70..90, "brightness $brightness")
    }

    private fun read(png: ByteArray) = ImageIO.read(ByteArrayInputStream(png))

    private fun pixels(
        image: BufferedImage,
        color: Color,
    ): Int {
        var count = 0
        for (x in 0 until image.width) for (y in 0 until image.height) if (image.getRGB(x, y) == color.rgb) count++
        return count
    }

    /** Colour difference in OKLab, times 100. */
    private fun deltaE(
        a: Color,
        b: Color,
    ): Double {
        val (l1, a1, b1) = oklab(a)
        val (l2, a2, b2) = oklab(b)
        return 100 * sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun oklab(color: Color): Triple<Double, Double, Double> {
        fun linear(value: Int) = (value / 255.0).let { if (it > 0.04045) ((it + 0.055) / 1.055).pow(2.4) else it / 12.92 }
        val (r, g, b) = Triple(linear(color.red), linear(color.green), linear(color.blue))
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Triple(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720403 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private companion object {
        /** More than the dot of a party in the legend, which is 12 px wide. */
        const val SWATCH_AREA = 256
    }
}
