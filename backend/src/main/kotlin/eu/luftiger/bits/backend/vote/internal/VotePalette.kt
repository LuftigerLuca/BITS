package eu.luftiger.bits.backend.vote.internal

import java.awt.Color

/**
 * The colours of the parties in the result image, in a fixed order. The first eight are a categorical palette checked
 * for colour-blind readers on a dark surface; the other 13 were picked in OKLCH, each as far as possible from all
 * before it, so up to 21 parties are told apart. Beyond that the colours repeat.
 */
object VotePalette {
    /** Those in no party. */
    val noParty = Color(0x9A9EA6)

    fun color(index: Int): Color = COLORS[index % COLORS.size]

    private val COLORS =
        listOf(
            0x3987E5,
            0xD95926,
            0x199E70,
            0xC98500,
            0xD55181,
            0x008300,
            0x9085E9,
            0xE66767,
            0x00DAFF,
            0x974DAD,
            0x7EDF7F,
            0xFF9AEB,
            0x857200,
            0xA9B9FF,
            0x839C00,
            0xFFA391,
            0x00849B,
            0xF5BA26,
            0x00A7C4,
            0xADB74D,
            0x24C8B1,
        ).map(::Color)
}
