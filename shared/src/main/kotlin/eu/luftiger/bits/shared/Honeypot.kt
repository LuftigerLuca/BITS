package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/** The honeypot as the backend knows it; the bot asks for it periodically. */
@Serializable
data class HoneypotState(
    /** `null` while no honeypot is set. */
    val channelId: String? = null,
    /** The warning the bot posted last, possibly still in a previous channel. */
    val warning: HoneypotWarning? = null,
    /** Whether the bot only deletes messages, until an admin lifts the pause. */
    val kicksPaused: Boolean = false,
)

@Serializable
data class HoneypotWarning(
    val channelId: String,
    val messageId: String,
)

/** The warning the bot now keeps; `null` once it removed it. */
@Serializable
data class HoneypotWarningReport(
    val warning: HoneypotWarning?,
)

/** What the bot did about one message in the honeypot, never with its content. */
@Serializable
data class HoneypotKickReport(
    val discordUserId: String,
    val memberName: String,
    val outcome: HoneypotKickOutcome,
    /** Why it was not kicked and what else did not work, in German for the dashboard. */
    val reason: String? = null,
)

@Serializable
enum class HoneypotKickOutcome {
    KICKED,

    /** Not tried, because the member is above the bot or kicks are paused. */
    SKIPPED,

    /** Tried, but it did not work, e.g. because the bot may not kick. */
    FAILED,
}

/** The bot reached the limit of kicks in [channelId]; ignored if that is no longer the honeypot. */
@Serializable
data class HoneypotPauseReport(
    val channelId: String,
    val kickCount: Int,
    val windowMinutes: Int,
)

/** The bot cannot keep its warning in the honeypot. */
@Serializable
data class HoneypotProblemReport(
    /** In German for the dashboard. */
    val reason: String,
)
