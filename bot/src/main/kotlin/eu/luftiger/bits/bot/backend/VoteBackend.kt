package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.CastVoteResponse
import eu.luftiger.bits.shared.CloseVoteResponse
import eu.luftiger.bits.shared.Elector
import eu.luftiger.bits.shared.OpenVote
import eu.luftiger.bits.shared.ParticipationResponse
import eu.luftiger.bits.shared.StartVoteRequest
import eu.luftiger.bits.shared.StartVoteResponse
import eu.luftiger.bits.shared.VoteActor
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteResultDto

/** The part of the backend's REST API that runs votes; [KtorBackendClient] implements it next to [BackendClient]. */
interface VoteBackend {
    suspend fun startVote(request: StartVoteRequest): StartVoteResponse

    suspend fun openVotes(): List<OpenVote>

    suspend fun vote(voteId: Long): VoteDto

    /** Ends the vote with a result, or without one if [cancel]. */
    suspend fun closeVote(
        voteId: Long,
        actor: VoteActor,
        cancel: Boolean,
    ): CloseVoteResponse

    suspend fun castVote(
        voteId: Long,
        discordUserId: String,
        choice: VoteChoice,
    ): CastVoteResponse

    /** Counts the votes of [roleMemberIds], the members of the role right now. */
    suspend fun participation(
        voteId: Long,
        roleMemberIds: List<String>,
    ): ParticipationResponse

    suspend fun reportVoteMessage(
        voteId: Long,
        messageId: String,
    )

    /** The members of the role at the end, which the result is counted by. */
    suspend fun reportElectorate(
        voteId: Long,
        electors: List<Elector>,
    )

    suspend fun voteResult(voteId: Long): VoteResultDto

    suspend fun markVoteResultPosted(voteId: Long)
}
