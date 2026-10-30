package eu.luftiger.bits.backend.overview.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

/** The key figures of one day; `null` is a figure that could not be read that day. Written by a native upsert only. */
@Entity
@Table(name = "overview_snapshot")
class OverviewSnapshot(
    @Id
    @Column(name = "day")
    val day: LocalDate,
    @Column(name = "members")
    val members: Int?,
    @Column(name = "verified_members")
    val verifiedMembers: Int?,
    @Column(name = "country_id")
    val countryId: String?,
    @Column(name = "citizens")
    val citizens: Int?,
    @Column(name = "active_citizens")
    val activeCitizens: Int?,
)
