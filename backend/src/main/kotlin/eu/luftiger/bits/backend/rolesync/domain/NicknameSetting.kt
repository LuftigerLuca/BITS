package eu.luftiger.bits.backend.rolesync.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Whether the role sync also sets the nicknames of linked members to their WarEra names; there is at most one row. */
@Entity
@Table(name = "nickname_setting")
class NicknameSetting(
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,
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
