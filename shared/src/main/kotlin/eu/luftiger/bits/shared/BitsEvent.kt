package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Event the backend publishes for the bot. Each subtype is identified by its `type` field. */
@Serializable
sealed interface BitsEvent {
    /** Epoch milliseconds at which the event happened. */
    val occurredAt: Long
}

/** Example event: published whenever the backend handles a ping. */
@Serializable
@SerialName("ping")
data class PingEvent(
    val discordUserId: String,
    override val occurredAt: Long,
) : BitsEvent

/** Published when a Discord account was linked to a WarEra account. */
@Serializable
@SerialName("account_linked")
data class AccountLinkedEvent(
    val discordUserId: String,
    val wareraUserId: String,
    /** Discord account the WarEra account was linked to before, if it was another one. */
    val previousDiscordUserId: String?,
    override val occurredAt: Long,
) : BitsEvent

fun BitsEvent.encode(): String = BitsJson.encodeToString(BitsEvent.serializer(), this)

fun decodeEvent(payload: String): BitsEvent = BitsJson.decodeFromString(BitsEvent.serializer(), payload)
