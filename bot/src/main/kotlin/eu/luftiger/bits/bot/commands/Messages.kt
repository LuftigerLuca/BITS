package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.TooManyRequestsException

/** When the backend does not answer. Members do not need to know that BITS has one, so it says BITS. */
internal const val BITS_UNAVAILABLE_MESSAGE = "BITS ist gerade nicht erreichbar, versuche es in ein paar Minuten noch einmal."

/** For a member who reached the backend's rate limit, see [TooManyRequestsException]. */
internal fun tooManyRequestsMessage(e: TooManyRequestsException): String =
    "Du hast das gerade zu oft versucht. Warte bitte ${e.retryAfterSeconds} Sekunden und versuche es dann noch einmal."
