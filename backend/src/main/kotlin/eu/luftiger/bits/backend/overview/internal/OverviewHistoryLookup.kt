package eu.luftiger.bits.backend.overview.internal

import eu.luftiger.bits.backend.overview.api.OverviewHistory
import eu.luftiger.bits.backend.overview.api.dto.DailySnapshot
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class OverviewHistoryLookup(
    private val snapshotRepository: OverviewSnapshotRepository,
) : OverviewHistory {
    override fun snapshots(
        from: LocalDate,
        to: LocalDate,
    ): List<DailySnapshot> =
        snapshotRepository.findByDayBetweenOrderByDay(from, to).map {
            DailySnapshot(it.day, it.members, it.verifiedMembers, it.countryId, it.citizens, it.activeCitizens)
        }
}
