package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.internal.VoteImageTheme.MAX_NON_VOTER_LINES
import java.awt.Graphics2D

/** Fits text of the image of a vote result into a given width. */
object VoteTextLayout {
    /** The names, comma-separated, in up to [MAX_NON_VOTER_LINES] lines; those that do not fit are counted at the end. */
    fun nameLines(
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
    fun wrap(
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

    fun ellipsize(
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
}
