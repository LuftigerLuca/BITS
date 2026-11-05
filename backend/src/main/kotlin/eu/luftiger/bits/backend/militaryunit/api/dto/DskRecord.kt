package eu.luftiger.bits.backend.militaryunit.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

@NamedInterface("api")
data class DskRecord(
    val id: Long,
    val name: String,
    /** `null` without a role. */
    val roleId: String?,
    /** By name. */
    val militaryUnits: List<DskUnitRecord>,
)

@NamedInterface("api")
data class DskUnitRecord(
    val muId: String,
    /** As WarEra named the unit when it was assigned. */
    val name: String,
    val assignedAt: Instant,
)
