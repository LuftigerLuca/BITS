package eu.luftiger.bits.backend.vote.internal

import java.awt.Color
import java.awt.image.BufferedImage

/** What admins may change about the result image; see [VoteResultSettingsService]. */
class VoteResultStyle(
    /** Cropped and darkened by [VoteBackground.cover] for each image. */
    val background: BufferedImage = VoteBackground.default,
    /** By WarEra's party ID; parties without one get their colour from [VotePalette]. */
    val partyColors: Map<String, Color> = emptyMap(),
    val noPartyColor: Color = VotePalette.noParty,
) {
    companion object {
        val DEFAULT = VoteResultStyle()
    }
}
