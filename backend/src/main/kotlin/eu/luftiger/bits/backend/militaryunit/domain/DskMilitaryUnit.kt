package eu.luftiger.bits.backend.militaryunit.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A military unit of a [Dsk]; it belongs to one at most. Its country was checked when it was assigned and is not checked
 * again.
 */
@Entity
@Table(name = "dsk_military_unit")
class DskMilitaryUnit(
    @Id
    @Column(name = "mu_id")
    val muId: String,
    @Column(name = "dsk_id", nullable = false)
    val dskId: Long,
    /** As WarEra named the unit when it was assigned. */
    @Column(name = "mu_name", nullable = false)
    val muName: String,
    @Column(name = "assigned_at", nullable = false)
    val assignedAt: Instant,
)
