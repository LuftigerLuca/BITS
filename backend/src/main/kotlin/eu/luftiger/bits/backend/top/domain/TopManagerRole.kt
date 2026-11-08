package eu.luftiger.bits.backend.top.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A role whose members may close and reopen TOPs; admins always may. */
@Entity
@Table(name = "top_manager_role")
class TopManagerRole(
    @Id
    @Column(name = "role_id")
    val roleId: String,
)
