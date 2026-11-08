package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A member as the backend needs them to decide about `/reprimand`, on either side of it. */
@Serializable
data class ReprimandMember(
    val discordUserId: String,
    /** As Discord shows the member, for the dashboard and the audit log. */
    val name: String,
    val avatarUrl: String? = null,
    val roleIds: List<String> = emptyList(),
    /** Owner of the guild or holder of the Administrator permission. */
    val administrator: Boolean = false,
    /** A bot account, which cannot be reprimanded. */
    val bot: Boolean = false,
)

/**
 * `/reprimand`: [actor] reprimands [target] for [reason]. The backend decides whether they may and stores the reprimand;
 * the bot then sets the timeout and sends the messages.
 */
@Serializable
data class ReprimandRequest(
    val actor: ReprimandMember,
    val target: ReprimandMember,
    val reason: String,
)

/** Outcome of [ReprimandRequest]. Each subtype is identified by its `type` field. */
@Serializable
sealed interface ReprimandResponse {
    /** Stored; the bot sets the timeout until [timeoutUntil]. */
    @Serializable
    @SerialName("reprimanded")
    data class Reprimanded(
        val reprimandId: Long,
        /** Reprimands of the target that count, this one included. */
        val count: Int,
        val timeoutMinutes: Int,
        /** Epoch milliseconds. */
        val timeoutUntil: Long,
    ) : ReprimandResponse

    /** The actor is neither admin nor member of a role that may reprimand. */
    @Serializable
    @SerialName("forbidden")
    data object Forbidden : ReprimandResponse

    /** The target cannot be reprimanded; nothing was stored. */
    @Serializable
    @SerialName("protected")
    data class Protected(
        val cause: ProtectedCause,
    ) : ReprimandResponse

    /** An admin switched reprimands off. */
    @Serializable
    @SerialName("disabled")
    data object Disabled : ReprimandResponse
}

@Serializable
enum class ProtectedCause {
    SELF,
    BOT,
    ADMIN,

    /** A member of a role that may reprimand others. */
    REPRIMANDER,
}

/** The bot could not set or lift the timeout of a reprimand; for the system log. */
@Serializable
data class ReprimandTimeoutProblemReport(
    val discordUserId: String,
    val memberName: String,
    /** In German for the dashboard. */
    val reason: String,
    /** The reprimand whose timeout could not be set; `null` when lifting one failed. */
    val reprimandId: Long? = null,
)

/**
 * The timeout a member should have after a reprimand was withdrawn: until [until], or none with `null`. The bot sets it
 * if the member is still on the server.
 */
@Serializable
@SerialName("reprimand_timeout")
data class ReprimandTimeoutEvent(
    val discordUserId: String,
    /** Epoch milliseconds; `null` lifts the timeout. */
    val until: Long?,
    override val occurredAt: Long,
) : BitsEvent
