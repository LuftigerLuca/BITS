package eu.luftiger.bits.backend.audit.api

import eu.luftiger.bits.backend.audit.api.dto.AuditRecord
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the audit module for reading entries outside of the audit log page. */
@NamedInterface("api")
interface AuditEntries {
    /** What the admin with the session subject [actorSubject] did, oldest first. */
    fun byActor(actorSubject: String): List<AuditRecord>
}
