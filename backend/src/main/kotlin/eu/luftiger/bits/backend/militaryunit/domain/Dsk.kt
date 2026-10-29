package eu.luftiger.bits.backend.militaryunit.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A DSK, a formation of the German armed forces in WarEra made up of military units, see [DskMilitaryUnit]. The linked
 * members of its units get [roleId]; without a role, it changes nothing.
 */
@Entity
@Table(name = "dsk")
class Dsk(
    /** E.g. "DSK1"; unique regardless of case. */
    @Column(name = "name", nullable = false)
    var name: String,
    /** A role without a rule, of no other DSK. */
    @Column(name = "role_id")
    var roleId: String?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
