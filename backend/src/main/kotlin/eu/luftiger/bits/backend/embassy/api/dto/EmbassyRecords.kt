package eu.luftiger.bits.backend.embassy.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

/** The embassy threads a member was added to by hand, and those the bot last reported them in. */
@NamedInterface("api")
data class EmbassyRecords(
    val manual: List<ManualMembership>,
    val reported: List<ReportedMembership>,
)

@NamedInterface("api")
data class ManualMembership(
    val countryName: String,
    val threadName: String,
    /** As the dashboard showed the member when they were added. */
    val memberName: String?,
    val addedAt: Instant,
)

@NamedInterface("api")
data class ReportedMembership(
    val countryName: String,
    val threadName: String,
    val memberName: String,
    val reason: String,
)
