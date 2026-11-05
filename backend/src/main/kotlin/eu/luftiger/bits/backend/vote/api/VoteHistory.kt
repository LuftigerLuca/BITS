package eu.luftiger.bits.backend.vote.api

import eu.luftiger.bits.backend.vote.api.dto.VoteRecord
import org.springframework.data.domain.Page
import org.springframework.modulith.NamedInterface

/**
 * Read access to the votes for the third-party API. Results come only with evaluated votes, so nobody outside learns an
 * interim count; they hold the counts, never a single ballot or the names of non-voters.
 */
@NamedInterface("api")
interface VoteHistory {
    /** The newest first. */
    fun page(
        page: Int,
        size: Int,
    ): Page<VoteRecord>

    fun find(voteId: Long): VoteRecord?
}
