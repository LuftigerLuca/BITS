package eu.luftiger.bits.backend.audit.api.dto

import eu.luftiger.bits.backend.audit.api.AuditAction
import org.springframework.modulith.NamedInterface
import java.time.Instant

/** One entry of the audit log; name and avatar as the session knew them at the time. */
@NamedInterface("api")
data class AuditRecord(
    val occurredAt: Instant,
    val actorName: String,
    val actorAvatarUrl: String?,
    val action: AuditAction,
    val description: String,
)
