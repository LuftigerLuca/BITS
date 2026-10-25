package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** The votes of one party, or of those in no party; all that is kept of the ballots. */
@Entity
@Table(name = "vote_party_result")
class PartyResult(
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,
    @Column(name = "position", nullable = false)
    val position: Int,
    /** `null` for those in no party. */
    @Column(name = "party_name")
    val partyName: String?,
    @Column(name = "yes_count", nullable = false)
    val yes: Int,
    @Column(name = "no_count", nullable = false)
    val no: Int,
    @Column(name = "abstain_count", nullable = false)
    val abstain: Int,
    /** Members of the party who could vote; at least those who did. */
    @Column(name = "eligible_count", nullable = false)
    val eligible: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    val voted: Int
        get() = yes + no + abstain
}
