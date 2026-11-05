package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.api.VoteHistory
import eu.luftiger.bits.backend.vote.api.dto.PartyTally
import eu.luftiger.bits.backend.vote.api.dto.VoteChoice
import eu.luftiger.bits.backend.vote.api.dto.VoteRecord
import eu.luftiger.bits.backend.vote.api.dto.VoteState
import eu.luftiger.bits.backend.vote.api.dto.VoteTally
import eu.luftiger.bits.backend.vote.domain.Choice
import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.domain.Vote
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class VoteHistoryLookup(
    private val voteRepository: VoteRepository,
    private val partyResultRepository: PartyResultRepository,
) : VoteHistory {
    override fun page(
        page: Int,
        size: Int,
    ): Page<VoteRecord> {
        // The ID breaks ties, so that no vote shows up on two pages.
        val votes = voteRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "id")))
        val records = toRecords(votes.content)
        return PageImpl(records, votes.pageable, votes.totalElements)
    }

    override fun find(voteId: Long): VoteRecord? = voteRepository.findByIdOrNull(voteId)?.let { toRecords(listOf(it)).single() }

    private fun toRecords(votes: List<Vote>): List<VoteRecord> {
        val evaluatedIds = votes.filter { it.status == VoteStatus.EVALUATED }.map { requireNotNull(it.id) }
        val parties =
            if (evaluatedIds.isEmpty()) {
                emptyMap()
            } else {
                partyResultRepository.findByVoteIdInOrderByPosition(evaluatedIds).groupBy { it.voteId }
            }
        return votes.map { vote ->
            val id = requireNotNull(vote.id)
            VoteRecord(
                id = id,
                question = vote.question,
                roleName = vote.roleName,
                state = vote.status.state,
                startedAt = vote.startedAt,
                endsAt = vote.endsAt,
                closedAt = vote.closedAt,
                thresholdPercent = vote.thresholdPercent,
                thresholdChoice = vote.thresholdChoice?.choice,
                endedByThreshold = vote.endedByThreshold(),
                result = if (vote.status == VoteStatus.EVALUATED) tally(parties[id].orEmpty()) else null,
            )
        }
    }

    private fun tally(parties: List<PartyResult>) =
        VoteTally(
            yes = parties.sumOf { it.yes },
            no = parties.sumOf { it.no },
            abstain = parties.sumOf { it.abstain },
            eligible = parties.sumOf { it.eligible },
            parties = parties.map { PartyTally(it.partyId, it.partyName, it.yes, it.no, it.abstain, it.eligible) },
        )

    private val VoteStatus.state: VoteState
        get() =
            when (this) {
                VoteStatus.OPEN -> VoteState.OPEN
                VoteStatus.COUNTING -> VoteState.COUNTING
                VoteStatus.EVALUATED -> VoteState.EVALUATED
                VoteStatus.CANCELLED -> VoteState.CANCELLED
            }

    /** Only yes or no can be the choice of a threshold. */
    private val Choice.choice: VoteChoice?
        get() =
            when (this) {
                Choice.YES -> VoteChoice.YES
                Choice.NO -> VoteChoice.NO
                Choice.ABSTAIN -> null
            }
}
