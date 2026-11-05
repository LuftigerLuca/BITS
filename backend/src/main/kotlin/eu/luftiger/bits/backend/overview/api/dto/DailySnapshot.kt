package eu.luftiger.bits.backend.overview.api.dto

import org.springframework.modulith.NamedInterface
import java.time.LocalDate

/** The key figures of one day, as recorded; `null` is a figure that could not be read that day. */
@NamedInterface("api")
data class DailySnapshot(
    val day: LocalDate,
    val members: Int?,
    val verifiedMembers: Int?,
    /** The country the citizens were counted for, the one set under "Verwaltung" on that day. */
    val countryId: String?,
    val citizens: Int?,
    val activeCitizens: Int?,
)
