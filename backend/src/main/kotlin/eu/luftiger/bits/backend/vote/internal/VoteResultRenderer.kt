package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.domain.PartyResult
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** What is kept of an evaluated vote: the counts per party and the names of the non-voters, no single ballot. */
class VoteResult(
    val question: String,
    /** Parties by size, those in no party last. */
    val parties: List<PartyResult>,
    val nonVoters: List<String>,
) {
    val yes = parties.sumOf { it.yes }
    val no = parties.sumOf { it.no }
    val abstain = parties.sumOf { it.abstain }
    val voted = yes + no + abstain
    val eligible = parties.sumOf { it.eligible }
}

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

    private fun percent(
        part: Int,
        whole: Int,
    ): Int = if (whole == 0) 0 else (part * 100.0 / whole).roundToInt()

    /** A bar for the total and one per party, stacked in percent of the votes cast, then the non-voters. */
    fun png(result: VoteResult): ByteArray {
        // Drawn twice: once to measure the height, then for real.
        val scratch = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val height = scratch.createGraphics().let { g -> draw(g, result).also { g.dispose() } }
        val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.color = BACKGROUND
            g.fillRect(0, 0, WIDTH, height)
            draw(g, result)
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    /** @return the height used */
    private fun draw(
        g: Graphics2D,
        result: VoteResult,
    ): Int {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        var y = PADDING

        g.font = SMALL
        g.color = MUTED
        y += g.fontMetrics.ascent
        g.drawString("Ergebnis der Abstimmung", PADDING, y)
        y += GAP / 2

        g.font = TITLE
        g.color = TEXT
        wrap(g, result.question, WIDTH - 2 * PADDING).forEach { line ->
            y += g.fontMetrics.height
            g.drawString(line, PADDING, y)
        }
        y += GAP

        y = bar(g, y, "Gesamt", result.yes, result.no, result.abstain, result.voted, result.eligible, total = true)
        y += GAP / 2
        result.parties.forEach { party ->
            y = bar(g, y, party.partyName ?: NO_PARTY, party.yes, party.no, party.abstain, party.voted, party.eligible, total = false)
        }

        y += GAP / 2
        y = legend(g, y)
        y += GAP

        g.font = HEADING
        g.color = TEXT
        y += g.fontMetrics.ascent
        g.drawString(if (result.nonVoters.isEmpty()) "Alle haben abgestimmt" else "Nicht abgestimmt (${result.nonVoters.size})", PADDING, y)
        if (result.nonVoters.isNotEmpty()) {
            g.font = BODY
            g.color = MUTED
            nameLines(g, result.nonVoters, WIDTH - 2 * PADDING).forEach { line ->
                y += g.fontMetrics.height
                g.drawString(line, PADDING, y)
            }
        }
        return y + PADDING
    }

    /** The names, comma-separated, in up to [MAX_NON_VOTER_LINES] lines; those that do not fit are counted at the end. */
    private fun nameLines(
        g: Graphics2D,
        names: List<String>,
        width: Int,
    ): List<String> {
        val metrics = g.fontMetrics
        val lines = mutableListOf<String>()
        var line = ""
        names.forEachIndexed { index, raw ->
            val name = ellipsize(g, raw, width)
            val candidate = if (line.isEmpty()) name else "$line, $name"
            val last = lines.size == MAX_NON_VOTER_LINES - 1
            val rest = names.size - index - 1
            // The last line keeps room for the count of the names that do not fit any more.
            val room = if (last && rest > 0) width - metrics.stringWidth(" … und $rest weitere") else width
            if (metrics.stringWidth(candidate) <= room) {
                line = candidate
            } else if (last) {
                return lines + "$line … und ${names.size - index} weitere"
            } else {
                lines += "$line,"
                line = name
            }
        }
        return lines + line
    }

    private fun bar(
        g: Graphics2D,
        top: Int,
        label: String,
        yes: Int,
        no: Int,
        abstain: Int,
        voted: Int,
        eligible: Int,
        total: Boolean,
    ): Int {
        val height = if (total) TOTAL_BAR_HEIGHT else BAR_HEIGHT
        val labelFont = if (total) HEADING else BODY
        g.font = labelFont
        g.color = TEXT
        val baseline = top + (height + g.fontMetrics.ascent - g.fontMetrics.descent) / 2
        g.drawString(ellipsize(g, label, LABEL_WIDTH - GAP / 2), PADDING, baseline)

        val x = PADDING + LABEL_WIDTH
        val width = WIDTH - 2 * PADDING - LABEL_WIDTH - PARTICIPATION_WIDTH
        if (voted == 0) {
            g.color = TRACK
            g.stroke = BasicStroke(2f)
            g.drawRoundRect(x, top, width, height, RADIUS, RADIUS)
            g.font = SMALL
            g.color = MUTED
            g.drawString("keine Stimmen", x + GAP / 2, baseline)
        } else {
            var segmentX = x
            var counted = 0
            listOf(yes to YES, no to NO, abstain to ABSTAIN).forEach { (count, color) ->
                // Each segment ends where the votes up to it end, so rounding never leaves a gap.
                counted += count
                val end = x + (counted * width.toDouble() / voted).roundToInt()
                val segmentWidth = end - segmentX
                if (count > 0 && segmentWidth > 0) {
                    g.color = color
                    g.fillRect(segmentX, top, segmentWidth, height)
                    val percentText = "${percent(count, voted)} %"
                    g.font = SMALL
                    if (g.fontMetrics.stringWidth(percentText) + 8 <= segmentWidth) {
                        g.color = Color.WHITE
                        g.drawString(percentText, segmentX + (segmentWidth - g.fontMetrics.stringWidth(percentText)) / 2, baseline)
                    }
                }
                segmentX = end
            }
        }

        g.font = SMALL
        g.color = MUTED
        val participation = "$voted von $eligible (${percent(voted, eligible)} %)"
        g.drawString(participation, WIDTH - PADDING - g.fontMetrics.stringWidth(participation), baseline)
        return top + height + ROW_GAP
    }

    private fun legend(
        g: Graphics2D,
        top: Int,
    ): Int {
        g.font = SMALL
        var x = PADDING + LABEL_WIDTH
        val baseline = top + g.fontMetrics.ascent
        listOf("Ja" to YES, "Nein" to NO, "Enthaltung" to ABSTAIN).forEach { (label, color) ->
            g.color = color
            g.fillRect(x, baseline - SWATCH, SWATCH, SWATCH)
            g.color = MUTED
            g.drawString(label, x + SWATCH + 6, baseline)
            x += SWATCH + 6 + g.fontMetrics.stringWidth(label) + GAP
        }
        g.color = MUTED
        val note = "Beteiligung"
        g.drawString(note, WIDTH - PADDING - g.fontMetrics.stringWidth(note), baseline)
        return baseline + g.fontMetrics.descent
    }

    /** Breaks [text] at spaces into lines of at most [width]; a word longer than a line is cut. */
    private fun wrap(
        g: Graphics2D,
        text: String,
        width: Int,
    ): List<String> {
        val metrics = g.fontMetrics
        val lines = mutableListOf<String>()
        var line = ""
        text.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (metrics.stringWidth(candidate) <= width) {
                line = candidate
            } else {
                if (line.isNotEmpty()) lines += line
                line = ellipsize(g, word, width)
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun ellipsize(
        g: Graphics2D,
        text: String,
        width: Int,
    ): String {
        val metrics = g.fontMetrics
        if (metrics.stringWidth(text) <= width) return text
        var end = text.length
        while (end > 0 && metrics.stringWidth(text.take(end) + "…") > width) end--
        return text.take(end) + "…"
    }

    private const val WIDTH = 1100
    private const val PADDING = 40
    private const val GAP = 24
    private const val ROW_GAP = 10
    private const val LABEL_WIDTH = 300
    private const val PARTICIPATION_WIDTH = 170
    private const val BAR_HEIGHT = 30
    private const val TOTAL_BAR_HEIGHT = 40
    private const val RADIUS = 6
    private const val SWATCH = 14

    /** Non-voters beyond are summed up; the text lists all of them. */
    private const val MAX_NON_VOTER_LINES = 6

    // Discord's colours, so the image fits in with its dark theme.
    private val BACKGROUND = Color(0x2B2D31)
    private val TEXT = Color(0xF2F3F5)
    private val MUTED = Color(0xB5BAC1)
    private val TRACK = Color(0x4E5058)
    private val YES = Color(0x23A55A)
    private val NO = Color(0xF23F43)
    private val ABSTAIN = Color(0x80848E)

    private val TITLE = Font(Font.SANS_SERIF, Font.BOLD, 30)
    private val HEADING = Font(Font.SANS_SERIF, Font.BOLD, 20)
    private val BODY = Font(Font.SANS_SERIF, Font.PLAIN, 18)
    private val SMALL = Font(Font.SANS_SERIF, Font.PLAIN, 15)
}
