package eu.luftiger.bits.backend.reprimand.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A role whose members may reprimand; admins always may. */
@Entity
@Table(name = "reprimand_role")
class ReprimandRole(
    @Id
    @Column(name = "role_id")
    val roleId: String,
)
