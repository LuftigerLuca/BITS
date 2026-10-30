package eu.luftiger.bits.backend.overview.internal

import eu.luftiger.bits.backend.overview.domain.OverviewSetting
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The history of the start page's key figures, one row per day. Each run reads the figures like the start page does;
 * the first one of a day writes the row, later ones only fill in what was missing, so a gap stays only for a figure
 * that could not be read all day.
 */
@Service
class OverviewHistoryService(
    private val overviewService: OverviewService,
    private val snapshotRepository: OverviewSnapshotRepository,
    private val settingRepository: OverviewSettingRepository,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /** @return whether the day's row was written or completed, `false` if it was complete already */
    fun recordSnapshot(): Boolean {
        val day = today()
        val existing = snapshotRepository.findByIdOrNull(day)
        if (existing?.members != null && existing.citizens != null) return false
        // Reads WarEra, so outside of the transaction.
        val overview = overviewService.overview()
        transactionTemplate.executeWithoutResult {
            snapshotRepository.record(
                day = day,
                members = overview.members,
                verifiedMembers = overview.verifiedMembers,
                countryId = overview.country?.id,
                citizens = overview.country?.citizens,
                activeCitizens = overview.country?.activeCitizens,
            )
        }
        return true
    }

    /**
     * The last [days] days up to today. Days without a row are left out, the dashboard shows them as gaps. Citizens
     * count only on days that counted the country set now.
     */
    fun history(days: Int): OverviewHistoryResponse {
        require(days in 1..MAX_DAYS) { "days must be between 1 and $MAX_DAYS" }
        val to = today()
        val from = to.minusDays(days - 1L)
        val countryId = settingRepository.findByIdOrNull(OverviewSetting.SINGLE_ROW_ID)?.countryId
        val snapshots =
            snapshotRepository.findByDayBetweenOrderByDay(from, to).map {
                val sameCountry = countryId != null && it.countryId == countryId
                OverviewHistoryDay(
                    day = it.day.toString(),
                    members = it.members,
                    verifiedMembers = it.verifiedMembers,
                    citizens = it.citizens.takeIf { sameCountry },
                    activeCitizens = it.activeCitizens.takeIf { sameCountry },
                )
            }
        return OverviewHistoryResponse(from.toString(), to.toString(), snapshots)
    }

    private fun today(): LocalDate = clock.instant().atZone(ZONE).toLocalDate()

    companion object {
        /** The day of the German Discord. */
        val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

        /** A year, also a leap year. */
        const val MAX_DAYS = 366
    }
}
