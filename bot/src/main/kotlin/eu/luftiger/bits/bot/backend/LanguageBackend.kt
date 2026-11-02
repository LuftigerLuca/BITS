package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.Language

/** The part of the backend's REST API for the language of a member; [KtorBackendClient] implements it. */
interface LanguageBackend {
    /** `null` if the member did not choose one. */
    suspend fun languagePreference(discordUserId: String): Language?

    /** `null` goes back to automatic. */
    suspend fun setLanguagePreference(
        discordUserId: String,
        language: Language?,
    )
}
