package eu.luftiger.bits.backend.vote.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

@NamedInterface("api")
data class VoteRecord(
    val id: Long,
    val question: String,
    /** The role whose members may vote, by its name when the vote started; `null` if it was unknown. */
    val roleName: String?,
    val state: VoteState,
    val startedAt: Instant,
    /** `null` for a vote that runs until somebody ends it. */
    val endsAt: Instant?,
    val closedAt: Instant?,
    /** The vote ends early once more than this share of the eligible members chose [thresholdChoice]. */
    val thresholdPercent: Int?,
    val thresholdChoice: VoteChoice?,
    val endedByThreshold: Boolean,
    /** Only for [VoteState.EVALUATED]. */
    val result: VoteTally?,
)

/** Own names for the states of a vote, so that a change of the data model does not change the API. */
@NamedInterface("api")
enum class VoteState {
    OPEN,
    COUNTING,
    EVALUATED,
    CANCELLED,
}

@NamedInterface("api")
enum class VoteChoice {
    YES,
    NO,
}

@NamedInterface("api")
data class VoteTally(
    val yes: Int,
    val no: Int,
    val abstain: Int,
    val eligible: Int,
    /** By size, those in no party last. */
    val parties: List<PartyTally>,
)

@NamedInterface("api")
data class PartyTally(
    /** `null` for those in no party and for votes evaluated before it was kept. */
    val partyId: String?,
    /** `null` for those in no party. */
    val partyName: String?,
    val yes: Int,
    val no: Int,
    val abstain: Int,
    val eligible: Int,
)
