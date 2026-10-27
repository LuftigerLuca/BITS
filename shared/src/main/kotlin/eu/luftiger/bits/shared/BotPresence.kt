package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the bot shows up in Discord's member list, as an admin set it in the dashboard. */
@Serializable
data class BotPresence(
    val status: BotStatus = BotStatus.ONLINE,
    /** `null` for no activity. */
    val activity: BotActivity? = null,
)

@Serializable
enum class BotStatus {
    ONLINE,
    IDLE,
    DO_NOT_DISTURB,
    INVISIBLE,
}

@Serializable
data class BotActivity(
    val type: BotActivityType,
    /** 1 to [MAX_TEXT_LENGTH] characters; the game, the show or, for [BotActivityType.CUSTOM], the whole status. */
    val text: String,
) {
    companion object {
        /** Discord's limit for the name of an activity. */
        const val MAX_TEXT_LENGTH = 128
    }
}

/** Without streaming, which needs a Twitch or YouTube URL. */
@Serializable
enum class BotActivityType {
    PLAYING,
    WATCHING,
    LISTENING,
    COMPETING,

    /** A status text of its own, without "Spielt" or the like in front. */
    CUSTOM,
}

/** Published when an admin changed the bot's presence; the bot also asks for it on start. */
@Serializable
@SerialName("bot_presence_changed")
data class BotPresenceChangedEvent(
    val presence: BotPresence,
    override val occurredAt: Long,
) : BitsEvent
