package eu.luftiger.bits.backend.audit.api

import org.springframework.modulith.NamedInterface

/**
 * Cross-module boundary of the audit module: every module with a dashboard action that changes something records it
 * here. Entries are only ever added; no API changes or deletes them.
 */
@NamedInterface("api")
interface AuditLog {
    /**
     * Records that [actor] did [action], described in German for the dashboard. Must be called inside the transaction
     * of the change, so that the change and its entry are committed together or not at all; without one it throws.
     */
    fun record(
        actor: AuditActor,
        action: AuditAction,
        description: String,
    )
}
