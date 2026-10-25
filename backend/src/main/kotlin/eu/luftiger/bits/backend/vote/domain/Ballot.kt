package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Who voted what; only kept until the vote is evaluated or cancelled. Written by [BallotRepository.cast] only, which
 * replaces an earlier one of the same member.
 */
@Entity
@Table(name = "vote_ballot")
class Ballot(
    @Id
    @Column(name = "id")
    val id: Long,
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,
    @Column(name = "discord_user_id", nullable = false)
    val discordUserId: String,
    /** Linked at the time of voting. */
    @Column(name = "warera_user_id", nullable = false)
    val wareraUserId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "choice", nullable = false)
    val choice: Choice,
    @Column(name = "cast_at", nullable = false)
    val castAt: Instant,
)

enum class Choice {
    YES,
    NO,
    ABSTAIN,
}
