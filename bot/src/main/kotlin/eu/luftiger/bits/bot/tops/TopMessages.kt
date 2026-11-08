package eu.luftiger.bits.bot.tops

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed

/** The public messages in a TOP post, in German like everything public. */
object TopMessages {
    private const val CLOSED_COLOR = 0x80848E
    private const val REOPENED_COLOR = 0x57F287

    /** [mention] is the member who closed it; mentions in an embed ping nobody. */
    fun closed(
        mention: String,
        reason: String?,
    ): MessageEmbed =
        EmbedBuilder()
            .setTitle("TOP geschlossen")
            .setColor(CLOSED_COLOR)
            .addField("Geschlossen von", mention, false)
            .apply { if (reason != null) addField("Grund", reason, false) }
            .build()

    fun reopened(mention: String): MessageEmbed =
        EmbedBuilder()
            .setTitle("TOP wieder geöffnet")
            .setColor(REOPENED_COLOR)
            .addField("Geöffnet von", mention, false)
            .build()
}
