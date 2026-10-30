package eu.luftiger.bits.backend.vote.internal

import java.awt.Color
import java.awt.Font
import java.awt.font.TextAttribute

/** The measures, colours and fonts of the image of a vote result. */
object VoteImageTheme {
    const val WIDTH = 1100

    /** Around the card, where the background shows. */
    const val MARGIN = 32
    const val PADDING = 48
    const val LEFT = MARGIN + PADDING
    const val CONTENT_WIDTH = WIDTH - 2 * LEFT
    const val SECTION_GAP = 32
    const val METER_HEIGHT = 8
    const val PLOT_HEIGHT = 300
    const val COLUMN_WIDTH = 128
    const val RADIUS = 10
    const val SEGMENT_GAP = 2

    /** Above the plot, for the count over the tallest column. */
    const val COUNT_SPACE = 64
    const val COLUMN_GAP = 40
    const val LEGEND_ROW = 52
    const val DOT = 12

    /** Non-voters beyond are summed up; the text lists all of them. */
    const val MAX_NON_VOTER_LINES = 6

    val CARD = Color(16, 17, 20, 185)
    val HAIRLINE = Color(255, 255, 255, 28)
    val TRACK = Color(255, 255, 255, 22)
    val HALF_LINE = Color(255, 255, 255, 150)
    val TEXT = Color(0xF4F4F5)
    val MUTED = Color(0xA1A1AA)
    val DARK_TEXT = Color(0x111214)
    val ACCENT = Color(0x3987E5)
    val YES = Color(0x22C55E)
    val NO = Color(0xEF4444)
    val ABSTAIN = Color(0xA1A1AA)

    private fun font(
        weight: String,
        size: Float,
        tracking: Float = 0f,
    ): Font {
        val base =
            requireNotNull(
                VoteImageTheme::class.java.getResourceAsStream("/vote/fonts/Geist-$weight.ttf"),
            ) { "Geist-$weight is missing" }
                .use { Font.createFont(Font.TRUETYPE_FONT, it) }
        return base.deriveFont(mapOf(TextAttribute.SIZE to size, TextAttribute.TRACKING to tracking))
    }

    val TITLE = font("SemiBold", 34f, -0.01f)
    val EYEBROW = font("Medium", 13f, 0.12f)
    val COUNT = font("SemiBold", 40f, -0.02f)
    val LABEL = font("Medium", 18f)
    val BODY_STRONG = font("Medium", 17f)
    val BODY = font("Regular", 17f)
    val SMALL = font("Regular", 15f)
    val SEGMENT = font("SemiBold", 15f)
}
