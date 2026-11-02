package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.TooManyRequestsException
import eu.luftiger.bits.shared.Language

/** When the backend does not answer. Members do not need to know that BITS has one, so it says BITS. */
internal fun bitsUnavailableMessage(language: Language): String =
    language.text(
        "BITS ist gerade nicht erreichbar, versuche es in ein paar Minuten noch einmal.",
        "BITS cannot be reached right now, please try again in a few minutes.",
    )

/** For a member who reached the backend's rate limit, see [TooManyRequestsException]. */
internal fun tooManyRequestsMessage(
    e: TooManyRequestsException,
    language: Language,
): String =
    language.text(
        "Du hast das gerade zu oft versucht. Warte bitte ${e.retryAfterSeconds} Sekunden und versuche es dann noch einmal.",
        "You tried this too often just now. Please wait ${e.retryAfterSeconds} seconds and try again.",
    )

internal fun wareraUnavailableMessage(language: Language): String =
    language.text(
        "WarEra ist gerade nicht erreichbar. Versuche es gleich noch einmal.",
        "WarEra cannot be reached right now. Please try again shortly.",
    )

internal fun backendTimeoutMessage(language: Language): String =
    language.text(
        "Das dauert gerade länger als erwartet. Versuche es gleich noch einmal.",
        "This is taking longer than expected. Please try again shortly.",
    )
