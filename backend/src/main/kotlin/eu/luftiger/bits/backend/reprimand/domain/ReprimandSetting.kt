package eu.luftiger.bits.backend.reprimand.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The timeout every reprimand brings; there is at most one row. */
@Entity
@Table(name = "reprimand_setting")
class ReprimandSetting(
    @Column(name = "timeout_minutes", nullable = false)
    var timeoutMinutes: Int,
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
