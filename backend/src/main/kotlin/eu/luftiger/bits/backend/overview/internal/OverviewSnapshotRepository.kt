package eu.luftiger.bits.backend.overview.internal

import eu.luftiger.bits.backend.overview.domain.OverviewSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface OverviewSnapshotRepository : JpaRepository<OverviewSnapshot, LocalDate> {
    /**
     * Writes the day's row in one statement, so several instances or a restart still leave one row per day. A later run
     * of the same day only fills in what was missing; the Discord figures and the country's figures count as one each.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO overview_snapshot (day, members, verified_members, country_id, citizens, active_citizens)
            VALUES (:day, :members, :verifiedMembers, :countryId, :citizens, :activeCitizens)
            ON CONFLICT (day) DO UPDATE SET
                members = CASE WHEN overview_snapshot.members IS NULL THEN excluded.members ELSE overview_snapshot.members END,
                verified_members = CASE WHEN overview_snapshot.members IS NULL
                    THEN excluded.verified_members ELSE overview_snapshot.verified_members END,
                country_id = CASE WHEN overview_snapshot.citizens IS NULL THEN excluded.country_id ELSE overview_snapshot.country_id END,
                citizens = CASE WHEN overview_snapshot.citizens IS NULL THEN excluded.citizens ELSE overview_snapshot.citizens END,
                active_citizens = CASE WHEN overview_snapshot.citizens IS NULL
                    THEN excluded.active_citizens ELSE overview_snapshot.active_citizens END
            WHERE overview_snapshot.members IS NULL OR overview_snapshot.citizens IS NULL
        """,
        nativeQuery = true,
    )
    fun record(
        day: LocalDate,
        members: Int?,
        verifiedMembers: Int?,
        countryId: String?,
        citizens: Int?,
        activeCitizens: Int?,
    )

    fun findByDayBetweenOrderByDay(
        from: LocalDate,
        to: LocalDate,
    ): List<OverviewSnapshot>
}
