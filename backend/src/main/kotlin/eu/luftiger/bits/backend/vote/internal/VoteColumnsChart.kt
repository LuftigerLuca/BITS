package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.ABSTAIN
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.BODY
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.COLUMN_WIDTH
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.CONTENT_WIDTH
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.COUNT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.COUNT_SPACE
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.DARK_TEXT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.DOT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.HAIRLINE
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.HALF_LINE
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.LABEL
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.LEFT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.MUTED
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.NO
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.PLOT_HEIGHT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.RADIUS
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.SEGMENT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.SEGMENT_GAP
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.SMALL
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.TEXT
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.TRACK
import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.YES
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import kotlin.math.roundToInt

/** The three columns for yes, no and abstain on one axis, each stacked by party in the same order. */
object VoteColumnsChart {
    private class Column(
        val label: String,
        val accent: Color,
        val count: (PartyResult) -> Int,
    )

    /** @return the bottom of the column labels */
    fun draw(
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

    private val COLUMNS =
        listOf(
            Column("Ja", YES) { it.yes },
            Column("Nein", NO) { it.no },
            Column("Enthaltung", ABSTAIN) { it.abstain },
        )
}
