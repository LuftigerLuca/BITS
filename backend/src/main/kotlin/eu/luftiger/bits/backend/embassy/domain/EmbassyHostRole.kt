package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A role whose members belong in every embassy thread, e.g. the own government. */
@Entity
@Table(name = "embassy_host_role")
class EmbassyHostRole(
    @Id
    @Column(name = "role_id")
    val roleId: String,
)
