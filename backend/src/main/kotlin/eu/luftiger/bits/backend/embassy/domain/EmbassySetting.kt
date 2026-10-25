package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The ambassador role; there is at most one row. */
@Entity
@Table(name = "embassy_setting")
class EmbassySetting(
    /** `null` while none is set. */
    @Column(name = "ambassador_role_id")
    var ambassadorRoleId: String?,
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
