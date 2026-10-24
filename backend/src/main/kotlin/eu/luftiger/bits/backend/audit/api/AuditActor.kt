package eu.luftiger.bits.backend.audit.api

import org.springframework.modulith.NamedInterface

/** The admin behind a change, as the session knew them at the time; see [AuditLog.record]. */
@NamedInterface("api")
data class AuditActor(
    /** `discord:<id>` or `admin:<username>`, the subject of the session. */
    val subject: String,
    val name: String,
    val avatarUrl: String?,
)
