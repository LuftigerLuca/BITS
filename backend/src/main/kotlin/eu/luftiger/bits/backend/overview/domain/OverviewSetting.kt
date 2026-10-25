package eu.luftiger.bits.backend.overview.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The WarEra country whose citizens the start page counts; there is at most one row. */
@Entity
@Table(name = "overview_setting")
class OverviewSetting(
    @Column(name = "country_id", nullable = false)
    var countryId: String,
    /** Name at the time the country was picked. */
    @Column(name = "country_name", nullable = false)
    var countryName: String,
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
