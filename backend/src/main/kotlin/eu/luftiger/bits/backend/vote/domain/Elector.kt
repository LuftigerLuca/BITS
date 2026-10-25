package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A member of the role when the vote ended; only kept until it is evaluated. */
@Entity
@Table(name = "vote_elector")
class Elector(
    @Column(name = "vote_id", nullable = false)
    val voteId: Long,
    @Column(name = "discord_user_id", nullable = false)
    val discordUserId: String,
    @Column(name = "name", nullable = false)
    val name: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
