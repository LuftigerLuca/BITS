package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A member who could have voted and did not, by the name Discord showed when the vote ended. */
@Entity
@Table(name = "vote_non_voter")
class NonVoter(
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,
    @Column(name = "position", nullable = false)
    val position: Int,
    @Column(name = "name", nullable = false)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
