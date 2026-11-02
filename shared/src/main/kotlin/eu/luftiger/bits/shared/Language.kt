package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/** A language BITS can write to a member in. */
@Serializable
enum class Language {
    GERMAN,
    ENGLISH,
    ;

    /** Picks the text for this language; a further language adds a parameter here, so no text can be forgotten. */
    fun text(
        german: String,
        english: String,
    ): String =
        when (this) {
            GERMAN -> german
            ENGLISH -> english
        }
}

/** Stores the language of the member; `null` for automatic, which deletes the setting. */
@Serializable
data class LanguagePreferenceRequest(
    val discordUserId: String,
    val language: Language? = null,
)

@Serializable
data class LanguagePreferenceResponse(
    /** `null` if the member did not choose one. */
    val language: Language? = null,
)
