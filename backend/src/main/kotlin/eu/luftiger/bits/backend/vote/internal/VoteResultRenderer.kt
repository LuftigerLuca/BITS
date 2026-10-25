package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.domain.PartyResult
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
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
 * Turns a [VoteResult] into the text and the image BITS posts and the dashboard shows. Both name parties and
 * non-voters, never who voted what.
 */
object VoteResultRenderer {
    const val NO_PARTY = "Parteilos"

    /** Plain text for the dashboard, or Discord markdown with names escaped if [markdown]. */
    fun text(
        result: VoteResult,
        markdown: Boolean,
    ): String {
        val escape: (String) -> String = if (markdown) ::escapeMarkdown else { it -> it }
        val title = if (markdown) "**Ergebnis:** ${escape(result.question)}" else "Ergebnis: ${result.question}"
        if (result.eligible == 0) return "$title\n\nNiemand mit der Rolle und einem verknüpften WarEra-Account konnte abstimmen."
        val total = "Gesamt: " + counts(result.yes, result.no, result.abstain, result.voted, result.eligible)
        val parties =
            result.parties.joinToString("\n") { party ->
                "${escape(party.partyName ?: NO_PARTY)}: " + counts(party.yes, party.no, party.abstain, party.voted, party.eligible)
            }
        val nonVoters =
            if (result.nonVoters.isEmpty()) {
                "Alle haben abgestimmt."
            } else {
                "Nicht abgestimmt (${result.nonVoters.size}): ${result.nonVoters.joinToString(", ", transform = escape)}"
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

    /** Escapes everything Discord reads as markdown or as a mention. */
    fun escapeMarkdown(text: String): String =
        text
            .replace(Regex("""([\\*_~`|>#\-\[\]()])"""), """\\$1""")
            .replace("@", "@​")

    /**
     * A card before [background], which is cropped and darkened, with the question and the participation, three
     * columns for yes, no and abstain on one axis, each stacked by party in the same order, a legend with the
     * participation of each party, and the non-voters.
     */
    fun png(
        result: VoteResult,
        background: BufferedImage = VoteBackground.default,
    ): ByteArray {
        // Drawn twice: once to measure the height, then for real.
        val scratch = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val height = scratch.createGraphics().let { g -> draw(g, result).also { g.dispose() } }
        val image = VoteBackground.cover(background, WIDTH, height)
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
            draw(g, result)
        } finally {
            g.dispose()
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    /** One colour per party from the palette in their order, grey for those in no party. */
    fun colors(parties: List<PartyResult>): List<Color> {
        var index = 0
        return parties.map { if (it.partyName == null) VotePalette.noParty else VotePalette.color(index++) }
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
    ): Int {
        smooth(g)
        var y = MARGIN + PADDING

        y = eyebrow(g, "Abstimmungsergebnis", y)
        y += 14
        g.font = TITLE
        g.color = TEXT
        wrap(g, result.question, CONTENT_WIDTH).forEach { line ->
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

        val colors = colors(result.parties)
        y = columns(g, y, result, colors)
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

    private class Column(
        val label: String,
        val accent: Color,
        val count: (PartyResult) -> Int,
    )

    /** @return the bottom of the column labels */
    private fun columns(
        g: Graphics2D,
        top: Int,
        result: VoteResult,
        colors: List<Color>,
    ): Int {
        val totals = COLUMNS.map { column -> result.parties.sumOf(column.count) }
        // High enough for the 50 % line even if no column reaches it.
        val axisMax = maxOf(1.0, totals.max().toDouble(), result.voted / 2.0)
        val plotTop = top + COUNT_SPACE
        val plotBottom = plotTop + PLOT_HEIGHT

        fun heightOf(value: Double) = (value * PLOT_HEIGHT / axisMax).roundToInt()

        g.color = HAIRLINE
        g.fillRect(LEFT, plotBottom, CONTENT_WIDTH, 1)

        val slot = CONTENT_WIDTH / COLUMNS.size
        var bottom = plotBottom
        COLUMNS.forEachIndexed { index, column ->
            val x = LEFT + index * slot + (slot - COLUMN_WIDTH) / 2
            val total = totals[index]
            val columnTop = plotBottom - heightOf(total.toDouble())

            if (total == 0) {
                // A stub, so an empty column still reads as a column.
                g.color = TRACK
                g.fillRoundRect(x, plotBottom - 4, COLUMN_WIDTH, 4, 4, 4)
            } else {
                // Rounded at the top, square at the baseline; the segments are cut out of this shape.
                val shape =
                    Area(
                        RoundRectangle2D.Float(
                            x.toFloat(),
                            columnTop.toFloat(),
                            COLUMN_WIDTH.toFloat(),
                            (
                                plotBottom - columnTop +
                                    RADIUS
                            ).toFloat(),
                            2f * RADIUS,
                            2f * RADIUS,
                        ),
                    )
                shape.intersect(Area(Rectangle(x, columnTop, COLUMN_WIDTH, plotBottom - columnTop)))
                val clip = g.clip
                g.clip(shape)
                var counted = 0
                var segmentBottom = plotBottom
                result.parties.forEachIndexed { partyIndex, party ->
                    val count = column.count(party)
                    if (count == 0) return@forEachIndexed
                    // Each segment ends where the votes up to it end, so rounding never leaves a gap.
                    counted += count
                    val segmentTop = plotBottom - heightOf(counted.toDouble())
                    // The gap between segments lets the card show through.
                    val gap = if (counted == total) 0 else SEGMENT_GAP
                    g.color = colors[partyIndex]
                    g.fillRect(x, segmentTop + gap, COLUMN_WIDTH, segmentBottom - segmentTop - gap)
                    segmentLabel(g, count, colors[partyIndex], x, segmentTop + gap, segmentBottom)
                    segmentBottom = segmentTop
                }
                g.clip = clip
            }

            g.font = COUNT
            g.color = TEXT
            val count = total.toString()
            val share = "${percent(total, result.voted)} %"
            val countWidth = g.fontMetrics.stringWidth(count)
            g.font = BODY
            val shareWidth = g.fontMetrics.stringWidth(share)
            val labelX = x + (COLUMN_WIDTH - countWidth - 8 - shareWidth) / 2
            g.font = COUNT
            g.drawString(count, labelX, columnTop - 14)
            g.font = BODY
            g.color = MUTED
            g.drawString(share, labelX + countWidth + 8, columnTop - 14)

            g.font = LABEL
            val labelWidth = DOT + 10 + g.fontMetrics.stringWidth(column.label)
            val labelLeft = x + (COLUMN_WIDTH - labelWidth) / 2
            val labelBaseline = plotBottom + 18 + g.fontMetrics.ascent
            g.color = column.accent
            g.fillOval(labelLeft, labelBaseline - g.fontMetrics.ascent / 2 - DOT / 2 - 1, DOT, DOT)
            g.color = TEXT
            g.drawString(column.label, labelLeft + DOT + 10, labelBaseline)
            bottom = labelBaseline + g.fontMetrics.descent
        }

        if (result.voted == 0) {
            g.font = BODY
            g.color = MUTED
            val note = "Es wurden keine Stimmen abgegeben."
            g.drawString(note, LEFT + (CONTENT_WIDTH - g.fontMetrics.stringWidth(note)) / 2, plotTop + PLOT_HEIGHT / 2)
        } else {
            // Half of the votes cast, over the columns, so a majority shows at a glance.
            val half = plotBottom - heightOf(result.voted / 2.0)
            g.color = HALF_LINE
            g.stroke = BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(6f, 5f), 0f)
            g.drawLine(LEFT, half, LEFT + CONTENT_WIDTH, half)
            g.font = SMALL
            g.color = TEXT
            val label = "50 %"
            g.drawString(label, LEFT + CONTENT_WIDTH - g.fontMetrics.stringWidth(label), half - 8)
        }
        return bottom
    }

    /** The count inside its segment, if the segment is tall and wide enough for it. */
    private fun segmentLabel(
        g: Graphics2D,
        count: Int,
        color: Color,
        x: Int,
        top: Int,
        bottom: Int,
    ) {
        g.font = SEGMENT
        val text = count.toString()
        val metrics = g.fontMetrics
        if (bottom - top < metrics.ascent + 12 || metrics.stringWidth(text) + 16 > COLUMN_WIDTH) return
        g.color = if (luminance(color) > 0.4) DARK_TEXT else Color.WHITE
        g.drawString(text, x + (COLUMN_WIDTH - metrics.stringWidth(text)) / 2, (top + bottom + metrics.ascent - metrics.descent) / 2)
    }

    /** Relative luminance after WCAG, to pick a readable text colour on [color]. */
    private fun luminance(color: Color): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

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
            g.drawString(ellipsize(g, party.partyName ?: NO_PARTY, columnWidth - DOT - 12), textX, nameBaseline)

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
        nameLines(g, names, CONTENT_WIDTH).forEach { line ->
            y += g.fontMetrics.ascent + 4
            g.drawString(line, LEFT, y)
            y += g.fontMetrics.descent + 4
        }
        return y
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

    /** Around the card, where the background shows. */
    private const val MARGIN = 32
    private const val PADDING = 48
    private const val LEFT = MARGIN + PADDING
    private const val CONTENT_WIDTH = WIDTH - 2 * LEFT
    private const val SECTION_GAP = 32
    private const val METER_HEIGHT = 8
    private const val PLOT_HEIGHT = 300
    private const val COLUMN_WIDTH = 128
    private const val RADIUS = 10
    private const val SEGMENT_GAP = 2

    /** Above the plot, for the count over the tallest column. */
    private const val COUNT_SPACE = 64
    private const val COLUMN_GAP = 40
    private const val LEGEND_ROW = 52
    private const val DOT = 12

    /** Non-voters beyond are summed up; the text lists all of them. */
    private const val MAX_NON_VOTER_LINES = 6

    private val CARD = Color(16, 17, 20, 185)
    private val HAIRLINE = Color(255, 255, 255, 28)
    private val TRACK = Color(255, 255, 255, 22)
    private val HALF_LINE = Color(255, 255, 255, 150)
    private val TEXT = Color(0xF4F4F5)
    private val MUTED = Color(0xA1A1AA)
    private val DARK_TEXT = Color(0x111214)
    private val ACCENT = Color(0x3987E5)
    private val YES = Color(0x22C55E)
    private val NO = Color(0xEF4444)
    private val ABSTAIN = Color(0xA1A1AA)

    private val COLUMNS =
        listOf(
            Column("Ja", YES) { it.yes },
            Column("Nein", NO) { it.no },
            Column("Enthaltung", ABSTAIN) { it.abstain },
        )

    private fun font(
        weight: String,
        size: Float,
        tracking: Float = 0f,
    ): Font {
        val base =
            requireNotNull(
                VoteResultRenderer::class.java.getResourceAsStream("/vote/fonts/Geist-$weight.ttf"),
            ) { "Geist-$weight is missing" }
                .use { Font.createFont(Font.TRUETYPE_FONT, it) }
        return base.deriveFont(mapOf(TextAttribute.SIZE to size, TextAttribute.TRACKING to tracking))
    }

    private val TITLE = font("SemiBold", 34f, -0.01f)
    private val EYEBROW = font("Medium", 13f, 0.12f)
    private val COUNT = font("SemiBold", 40f, -0.02f)
    private val LABEL = font("Medium", 18f)
    private val BODY_STRONG = font("Medium", 17f)
    private val BODY = font("Regular", 17f)
    private val SMALL = font("Regular", 15f)
    private val SEGMENT = font("SemiBold", 15f)
}
