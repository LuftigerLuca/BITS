package eu.luftiger.bits.backend.rolesync.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The roles for verified and unverified members; there is at most one row, written once an admin sets them. */
@Entity
@Table(name = "status_role_setting")
class StatusRoleSetting(
    /** `null` for none. */
    @Column(name = "verified_role_id")
    var verifiedRoleId: String?,
    /** `null` for none. */
    @Column(name = "unverified_role_id")
    var unverifiedRoleId: String?,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    @Id
    @Column(name = "id")
    val id: Short = SINGLE_ROW_ID

    companion object {
        const val SINGLE_ROW_ID: Short = 1
    }
}
