package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A role whose members may start, end and cancel votes; admins always may. */
@Entity
@Table(name = "vote_manager_role")
class VoteManagerRole(
    @Id
    @Column(name = "role_id")
    val roleId: String,
)
