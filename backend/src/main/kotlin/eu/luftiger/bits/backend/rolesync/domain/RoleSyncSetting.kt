package eu.luftiger.bits.backend.rolesync.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Settings of the role sync; there is at most one row, written once an admin changes one. */
@Entity
@Table(name = "role_sync_setting")
class RoleSyncSetting(
    /** Whether the bot holds back removals from many holders of a role at once, see `RoleSyncEvent.removalLimit`. */
    @Column(name = "removal_limit_enabled", nullable = false)
    var removalLimitEnabled: Boolean,
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
