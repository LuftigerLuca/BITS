package eu.luftiger.bits.backend.vote.internal

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The picture behind the result image. Any picture is scaled to cover the image, cropped around its centre and
 * darkened, so text and columns stay readable whatever it shows.
 */
object VoteBackground {
    /** The neon eagle of the login page. */
    val default: BufferedImage by lazy {
        requireNotNull(javaClass.getResource("/vote/background.jpg")) { "vote/background.jpg is missing" }
            .let(ImageIO::read)
    }

    /** Share of black laid over the picture. */
    const val DIM = 0.68f

    /** [source] scaled to cover [width] × [height], cropped around its centre and darkened by [DIM]. */
    fun cover(
        source: BufferedImage,
        width: Int,
        height: Int,
    ): BufferedImage {
        val scale = max(width.toDouble() / source.width, height.toDouble() / source.height)
        val scaledWidth = (source.width * scale).roundToInt()
        val scaledHeight = (source.height * scale).roundToInt()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(source, (width - scaledWidth) / 2, (height - scaledHeight) / 2, scaledWidth, scaledHeight, null)
            g.color = Color(0f, 0f, 0f, DIM)
            g.fillRect(0, 0, width, height)
        } finally {
            g.dispose()
        }
        return image
    }
}
