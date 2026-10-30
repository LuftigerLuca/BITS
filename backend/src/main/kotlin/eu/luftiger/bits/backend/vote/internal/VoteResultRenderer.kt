package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.ACCENT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.BODY
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.BODY_STRONG
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.CARD
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.COLUMN_GAP
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.CONTENT_WIDTH
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.DOT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.EYEBROW
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.HAIRLINE
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.LEFT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.LEGEND_ROW
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.MARGIN
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.METER_HEIGHT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.MUTED
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.PADDING
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.SECTION_GAP
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.SMALL
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.TEXT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.TITLE
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.TRACK
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.WIDTH
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Turns a [VoteResult] into the image BITS posts and the dashboard shows, and the text of the dashboard. Both name
 * parties and non-voters, never who voted what.
 */
object VoteResultRenderer {
    const val NO_PARTY = "Parteilos"

    /** Plain text for the dashboard. */
    fun text(result: VoteResult): String {
        val title = "Ergebnis: ${result.question}"
        if (result.eligible == 0) return "$title\n\nNiemand mit der Rolle und einem verknüpften WarEra-Account konnte abstimmen."
        val total = "Gesamt: " + counts(result.yes, result.no, result.abstain, result.voted, result.eligible)
        val parties =
            result.parties.joinToString("\n") { party ->
                "${party.partyName ?: NO_PARTY}: " + counts(party.yes, party.no, party.abstain, party.voted, party.eligible)
            }
        val nonVoters =
            if (result.nonVoters.isEmpty()) {
                "Alle haben abgestimmt."
            } else {
                "Nicht abgestimmt (${result.nonVoters.size}): ${result.nonVoters.joinToString(", ")}"
            }
        return "$title\n$total\n\n$parties\n\n$nonVoters"
    }

    /** E.g. "4 Ja · 1 Nein · 0 Enthaltung · 5 von 7 haben abgestimmt (71 %)". */
    private fun counts(
        yes: Int,
        no: Int,
        abstain: Int,
        voted: Int,
        eligible: Int,
    ): String = "$yes Ja · $no Nein · $abstain Enthaltung · $voted von $eligible haben abgestimmt (${percent(voted, eligible)} %)"

    /**
     * A card before [background], which is cropped and darkened, with the question and the participation, three
     * columns for yes, no and abstain on one axis, each stacked by party in the same order, a legend with the
     * participation of each party, and the non-voters.
     */
    fun png(
        result: VoteResult,
        style: VoteResultStyle = VoteResultStyle.DEFAULT,
    ): ByteArray {
        // Drawn twice: once to measure the height, then for real.
        val scratch = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val colors = colors(result.parties, style)
        val height = scratch.createGraphics().let { g -> draw(g, result, colors).also { g.dispose() } }
        val image = VoteBackground.cover(style.background, WIDTH, height)
        val g = image.createGraphics()
        try {
            smooth(g)
            val card =
                RoundRectangle2D.Float(
                    MARGIN.toFloat(),
                    MARGIN.toFloat(),
                    (WIDTH - 2 * MARGIN).toFloat(),
                    (height - 2 * MARGIN).toFloat(),
                    32f,
                    32f,
                )
            g.color = CARD
            g.fill(card)
            g.color = HAIRLINE
            g.stroke = BasicStroke(1f)
            g.draw(card)
            draw(g, result, colors)
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    /**
     * One colour per party: the one an admin picked for it, else the palette's in their order. A party with a colour of
     * its own still takes its place in the palette, so the others keep theirs.
     */
    fun colors(
        parties: List<PartyResult>,
        style: VoteResultStyle = VoteResultStyle.DEFAULT,
    ): List<Color> {
        var index = 0
        return parties.map { party ->
            if (party.partyName == null) {
                style.noPartyColor
            } else {
                val palette = VotePalette.color(index++)
                party.partyId?.let(style.partyColors::get) ?: palette
            }
        }
    }

    private fun smooth(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    /** @return the height of the whole image */
    private fun draw(
        g: Graphics2D,
        result: VoteResult,
        colors: List<Color>,
    ): Int {
        smooth(g)
        var y = MARGIN + PADDING

        y = eyebrow(g, "Abstimmungsergebnis", y)
        y += 14
        g.font = TITLE
        g.color = TEXT
        VoteTextLayout.wrap(g, result.question, CONTENT_WIDTH).forEach { line ->
            y += g.fontMetrics.ascent
            g.drawString(line, LEFT, y)
            y += g.fontMetrics.descent + 6
        }
        y += 18

        if (result.parties.isEmpty()) {
            g.font = BODY
            g.color = MUTED
            y += g.fontMetrics.ascent
            g.drawString("Niemand mit der Rolle und einem verknüpften WarEra-Account konnte abstimmen.", LEFT, y)
            return y + PADDING + MARGIN
        }
        y = participationMeter(g, y, result)
        y += SECTION_GAP

        y = VoteColumnsChart.draw(g, y, result, colors)
        y = divider(g, y + SECTION_GAP)
        y = legend(g, y + SECTION_GAP, result.parties, colors)
        y = divider(g, y + SECTION_GAP)
        y = nonVoters(g, y + SECTION_GAP, result.nonVoters)
        return y + PADDING + MARGIN
    }

    /** A small uppercase label above a section. */
    private fun eyebrow(
        g: Graphics2D,
        text: String,
        top: Int,
    ): Int {
        g.font = EYEBROW
        g.color = MUTED
        val baseline = top + g.fontMetrics.ascent
        g.drawString(text.uppercase(), LEFT, baseline)
        return baseline + g.fontMetrics.descent
    }

    private fun divider(
        g: Graphics2D,
        y: Int,
    ): Int {
        g.color = HAIRLINE
        g.fillRect(LEFT, y, CONTENT_WIDTH, 1)
        return y + 1
    }

    /** "29 von 40 haben abgestimmt · 73 %" over a slim bar. */
    private fun participationMeter(
        g: Graphics2D,
        top: Int,
        result: VoteResult,
    ): Int {
        g.font = BODY_STRONG
        g.color = TEXT
        val baseline = top + g.fontMetrics.ascent
        val text = "${result.voted} von ${result.eligible} haben abgestimmt"
        g.drawString(text, LEFT, baseline)
        val x = LEFT + g.fontMetrics.stringWidth(text)
        g.font = BODY
        g.color = MUTED
        g.drawString("  ·  ${percent(result.voted, result.eligible)} %", x, baseline)

        val barTop = baseline + 14
        g.color = TRACK
        g.fillRoundRect(LEFT, barTop, CONTENT_WIDTH, METER_HEIGHT, METER_HEIGHT, METER_HEIGHT)
        val filled = if (result.eligible == 0) 0 else (CONTENT_WIDTH * result.voted.toDouble() / result.eligible).roundToInt()
        if (filled > 0) {
            g.color = ACCENT
            g.fillRoundRect(LEFT, barTop, maxOf(filled, METER_HEIGHT), METER_HEIGHT, METER_HEIGHT, METER_HEIGHT)
        }
        return barTop + METER_HEIGHT
    }

    /** E.g. "6 von 7 haben abgestimmt (86 %)". */
    private fun participation(
        voted: Int,
        eligible: Int,
    ) = "$voted von $eligible haben abgestimmt (${percent(voted, eligible)} %)"

    /** Each party with its colour, its name and below it its participation, in two columns read top to bottom. */
    private fun legend(
        g: Graphics2D,
        top: Int,
        parties: List<PartyResult>,
        colors: List<Color>,
    ): Int {
        val start = eyebrow(g, "Parteien", top) + 10
        val columnWidth = (CONTENT_WIDTH - COLUMN_GAP) / 2
        val rows = (parties.size + 1) / 2
        parties.forEachIndexed { index, party ->
            val x = LEFT + (index / rows) * (columnWidth + COLUMN_GAP)
            val rowTop = start + (index % rows) * LEGEND_ROW
            val textX = x + DOT + 12

            g.font = BODY_STRONG
            val nameBaseline = rowTop + 6 + g.fontMetrics.ascent
            g.color = colors[index]
            g.fillOval(x, nameBaseline - g.fontMetrics.ascent / 2 - DOT / 2 + 1, DOT, DOT)
            g.color = TEXT
            g.drawString(VoteTextLayout.ellipsize(g, party.partyName ?: NO_PARTY, columnWidth - DOT - 12), textX, nameBaseline)

            g.font = SMALL
            g.color = MUTED
            g.drawString(participation(party.voted, party.eligible), textX, nameBaseline + 6 + g.fontMetrics.ascent)
        }
        return start + rows * LEGEND_ROW
    }

    private fun nonVoters(
        g: Graphics2D,
        top: Int,
        names: List<String>,
    ): Int {
        if (names.isEmpty()) return eyebrow(g, "Alle haben abgestimmt", top)
        var y = eyebrow(g, "Nicht abgestimmt · ${names.size}", top) + 6
        g.font = SMALL
        g.color = MUTED
        VoteTextLayout.nameLines(g, names, CONTENT_WIDTH).forEach { line ->
            y += g.fontMetrics.ascent + 4
            g.drawString(line, LEFT, y)
            y += g.fontMetrics.descent + 4
        }
        return y
    }
}
