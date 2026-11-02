package eu.luftiger.bits.bot.language

import eu.luftiger.bits.bot.backend.LanguageBackend
import eu.luftiger.bits.shared.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.Interaction
import org.slf4j.LoggerFactory

/**
 * The language BITS writes to a member in, when only that member reads it: what they chose with `/language`, then the
 * language of their Discord client, then German if that is not known, as in direct messages.
 *
 * Asking the backend never fails and takes at most [LOOKUP_TIMEOUT_MILLIS], so an outage only costs the chosen language.
 * Answers are kept for [CACHE_MILLIS], which is also how long a member who left and came back may still see their old one.
 */
class Languages(
    private val backend: LanguageBackend,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Cached(
        val language: Language?,
        val at: Long,
    )

    private val cache =
        object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>) = size > MAX_CACHED_MEMBERS
        }

    @Volatile
    private var failing = false

    suspend fun of(interaction: Interaction): Language = stored(interaction.user.id) ?: ofLocale(interaction.userLocale)

    /** For answers that cannot wait for the backend: without a known choice, the language of the client decides. */
    fun cachedOf(interaction: Interaction): Language = cached(interaction.user.id)?.language ?: ofLocale(interaction.userLocale)

    /** Direct messages come without a client language. */
    suspend fun ofDirectMessage(discordUserId: String): Language = stored(discordUserId) ?: Language.GERMAN

    /** @throws Exception if the backend does not take it */
    suspend fun set(
        discordUserId: String,
        language: Language?,
    ) {
        backend.setLanguagePreference(discordUserId, language)
        remember(discordUserId, language)
    }

    private suspend fun stored(discordUserId: String): Language? {
        cached(discordUserId)?.let { return it.language }
        val answer =
            withTimeoutOrNull(LOOKUP_TIMEOUT_MILLIS) {
                try {
                    Cached(backend.languagePreference(discordUserId), now())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Once per outage, not every command.
                    if (!failing) log.warn("Could not load the language of user {}", discordUserId, e)
                    failing = true
                    null
                }
            } ?: return null
        failing = false
        synchronized(cache) { cache[discordUserId] = answer }
        return answer.language
    }

    private fun remember(
        discordUserId: String,
        language: Language?,
    ) {
        synchronized(cache) { cache[discordUserId] = Cached(language, now()) }
    }

    private fun cached(discordUserId: String): Cached? =
        synchronized(cache) { cache[discordUserId] }?.takeIf { now() - it.at < CACHE_MILLIS }

    companion object {
        private const val LOOKUP_TIMEOUT_MILLIS = 1_500L
        private const val CACHE_MILLIS = 600_000L
        private const val MAX_CACHED_MEMBERS = 10_000

        /** `de*` is German and everything else English, except a client language the bot does not know. */
        fun ofLocale(locale: DiscordLocale?): Language =
            when {
                locale == null || locale == DiscordLocale.UNKNOWN || locale.locale.startsWith("de") -> Language.GERMAN
                else -> Language.ENGLISH
            }
    }
}
