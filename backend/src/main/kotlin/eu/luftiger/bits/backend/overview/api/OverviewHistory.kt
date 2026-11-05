package eu.luftiger.bits.backend.overview.api

import eu.luftiger.bits.backend.overview.api.dto.DailySnapshot
import org.springframework.modulith.NamedInterface
import java.time.LocalDate

/** Read access to the key figures of the past days for the third-party API. */
@NamedInterface("api")
interface OverviewHistory {
    /** The days from [from] to [to], both included, in order; days without a row are left out. */
    fun snapshots(
        from: LocalDate,
        to: LocalDate,
    ): List<DailySnapshot>
}
