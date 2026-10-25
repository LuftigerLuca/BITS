package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VoteChoice {
    YES,
    NO,
    ABSTAIN,
}

@Serializable
enum class VoteState {
    OPEN,

    /** Ended; the result follows once the bot has reported the role members and WarEra has answered. */
    COUNTING,
    EVALUATED,

    /** Ended without a result. */
    CANCELLED,
}

/**
 * A member who starts, ends or cancels a vote in Discord. The backend decides with [roleIds] and [administrator]
 * whether they may, since it knows which roles may manage votes.
 */
@Serializable
data class VoteActor(
    val discordUserId: String,
    /** As Discord shows the member, for the audit log. */
    val name: String,
    val avatarUrl: String?,
    val roleIds: List<String>,
    /** Owner of the guild or holder of the Administrator permission. */
    val administrator: Boolean,
)

@Serializable
data class StartVoteRequest(
    val actor: VoteActor,
    val question: String,
    /** Members of this role may vote. */
    val roleId: String,
    val channelId: String,
    /** Epoch milliseconds, `null` for a vote that runs until somebody ends it. */
    val endsAt: Long?,
)

/** Outcome of starting a vote. Each subtype is identified by its `type` field. */
@Serializable
sealed interface StartVoteResponse {
    /** The bot posts it once it gets the [VoteStartedEvent]. */
    @Serializable
    @SerialName("started")
    data class Started(
        val voteId: Long,
    ) : StartVoteResponse

    @Serializable
    @SerialName("forbidden")
    data object Forbidden : StartVoteResponse

    /** The bot cannot post in the channel, as far as the backend knows. */
    @Serializable
    @SerialName("channel_not_postable")
    data object ChannelNotPostable : StartVoteResponse
}

@Serializable
data class OpenVotesResponse(
    val votes: List<OpenVote>,
)

@Serializable
data class OpenVote(
    val id: Long,
    val question: String,
)

/** Ends a vote with a result or, with [cancel], without one. */
@Serializable
data class CloseVoteRequest(
    val actor: VoteActor,
    val cancel: Boolean,
)

@Serializable
sealed interface CloseVoteResponse {
    @Serializable
    @SerialName("closed")
    data object Closed : CloseVoteResponse

    @Serializable
    @SerialName("forbidden")
    data object Forbidden : CloseVoteResponse

    /** Ended already, or there is no such vote. */
    @Serializable
    @SerialName("not_open")
    data object NotOpen : CloseVoteResponse
}

/** A click on a button of the vote; the bot has checked that the member has the role. */
@Serializable
data class CastVoteRequest(
    val discordUserId: String,
    val choice: VoteChoice,
)

@Serializable
sealed interface CastVoteResponse {
    @Serializable
    @SerialName("cast")
    data class Cast(
        /** The member's vote before, `null` for a first one. */
        val previous: VoteChoice?,
    ) : CastVoteResponse

    @Serializable
    @SerialName("not_linked")
    data object NotLinked : CastVoteResponse

    @Serializable
    @SerialName("closed")
    data object Closed : CastVoteResponse
}

/** Who has the role right now; only they count for the participation. */
@Serializable
data class ParticipationRequest(
    val roleMemberIds: List<String>,
)

@Serializable
data class ParticipationResponse(
    /** Members of the role who voted. */
    val voted: Int,
    /** Members of the role with a linked account; members without one cannot vote. */
    val eligible: Int,
)

/** What the bot needs to show a vote in Discord. */
@Serializable
data class VoteDto(
    val id: Long,
    val question: String,
    val roleId: String,
    val channelId: String,
    /** Epoch milliseconds, `null` for a vote without end. */
    val endsAt: Long?,
    val state: VoteState,
    /** The bot's message with the buttons, `null` until it is posted. */
    val messageId: String?,
)

@Serializable
data class VoteMessageReport(
    val messageId: String,
)

/** The members of the role when the vote ended, which the backend counts by. */
@Serializable
data class ElectorateReport(
    val members: List<Elector>,
)

@Serializable
data class Elector(
    val discordUserId: String,
    /** As Discord shows the member; non-voters are listed by it. */
    val name: String,
)

@Serializable
data class VoteResultDto(
    /** Discord markdown; may be longer than one message. */
    val text: String,
    /** PNG, Base64-encoded. */
    val image: String,
    /** Whether the bot has posted it already. */
    val posted: Boolean,
)

/** A vote was started; the bot posts it. */
@Serializable
@SerialName("vote_started")
data class VoteStartedEvent(
    val voteId: Long,
    override val occurredAt: Long,
) : BitsEvent

/**
 * A vote ended or was cancelled. The bot closes its message and, unless it was cancelled, reports the members of the
 * role, see [ElectorateReport]. The backend sends it again if the members do not come.
 */
@Serializable
@SerialName("vote_closed")
data class VoteClosedEvent(
    val voteId: Long,
    override val occurredAt: Long,
) : BitsEvent

/** The result of a vote is ready; the bot posts it, see [VoteResultDto]. */
@Serializable
@SerialName("vote_evaluated")
data class VoteEvaluatedEvent(
    val voteId: Long,
    override val occurredAt: Long,
) : BitsEvent
