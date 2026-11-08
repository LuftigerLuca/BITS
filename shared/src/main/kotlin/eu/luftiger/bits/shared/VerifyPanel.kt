package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/** The channel with the button to verify, as the backend knows it; the bot asks for it periodically. */
@Serializable
data class VerifyPanelState(
    /** `null` while no channel is set. */
    val channelId: String? = null,
    /** The message the bot posted last, possibly still in a previous channel. */
    val message: VerifyPanelMessage? = null,
)

@Serializable
data class VerifyPanelMessage(
    val channelId: String,
    val messageId: String,
)

/** The message the bot now keeps; `null` once it removed it. */
@Serializable
data class VerifyPanelReport(
    val message: VerifyPanelMessage?,
)

/** The bot cannot keep its message in the channel. */
@Serializable
data class VerifyPanelProblemReport(
    /** In German for the dashboard. */
    val reason: String,
)
