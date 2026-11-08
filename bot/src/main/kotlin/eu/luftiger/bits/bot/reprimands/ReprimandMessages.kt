package eu.luftiger.bits.bot.reprimands

import eu.luftiger.bits.shared.Language
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed

/** The texts of a reprimand: the public one in German, the others in the language of whoever reads them. */
object ReprimandMessages {
    private const val COLOR = 0xED4245

    /** [mention] is the reprimanded member; mentions in an embed ping nobody. [timeoutMinutes] is `null` without timeout. */
    fun public(
        mention: String,
        reason: String,
        count: Int,
        timeoutMinutes: Int?,
    ): MessageEmbed =
        EmbedBuilder()
            .setTitle("Rüge")
            .setColor(COLOR)
            .addField("Mitglied", mention, true)
            .addField("Anzahl", "$count. Rüge", true)
            .apply { if (timeoutMinutes != null) addField("Timeout", duration(timeoutMinutes, Language.GERMAN), true) }
            .addField("Grund", reason, false)
            .build()

    fun directMessage(
        guildName: String,
        reason: String,
        count: Int,
        timeoutMinutes: Int?,
        language: Language,
    ): String {
        val timeout =
            timeoutMinutes?.let {
                language.text(
                    " Du hast einen Timeout von ${duration(it, language)}.",
                    " You are timed out for ${duration(it, language)}.",
                )
            } ?: ""
        return language.text(
            "Du wurdest auf **$guildName** gerügt. Grund: $reason\nDas ist deine $count. Rüge.$timeout",
            "You were reprimanded on **$guildName**. Reason: $reason\nThis is reprimand number $count for you.$timeout",
        )
    }

    /** In the largest unit that fits exactly, as the dashboard sets it. */
    fun duration(
        minutes: Int,
        language: Language,
    ): String =
        when {
            minutes % DAY == 0 -> plural(minutes / DAY, language.text("Tag", "day"), language.text("Tage", "days"))
            minutes % HOUR == 0 -> plural(minutes / HOUR, language.text("Stunde", "hour"), language.text("Stunden", "hours"))
            else -> plural(minutes, language.text("Minute", "minute"), language.text("Minuten", "minutes"))
        }

    private fun plural(
        count: Int,
        one: String,
        many: String,
    ) = "$count ${if (count == 1) one else many}"

    private const val HOUR = 60
    private const val DAY = 24 * HOUR
}
