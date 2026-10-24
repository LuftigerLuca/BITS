package eu.luftiger.bits.backend.audit.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import kotlinx.serialization.Serializable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The audit log in the dashboard; every admin sees the entries of all admins. Read-only on purpose: entries cannot be
 * changed or deleted. Only for admins, see the security config of `/web/admin/`.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/audit-log")
class AuditLogController(
    private val service: AuditLogService,
) {
    /** Newest first, [AuditLogService.PAGE_SIZE] at a time; the next page starts [before] the last entry of this one. */
    @GetMapping
    fun entries(
        @RequestParam(required = false) actor: String?,
        @RequestParam(required = false) action: AuditAction?,
        @RequestParam(required = false) before: Long?,
    ): AuditLogPage = service.page(actor, action, before)

    /** The admins to filter by: everybody who appears in the log. */
    @GetMapping("/actors")
    fun actors(): List<AuditActorResponse> = service.actors()
}

@Serializable
data class AuditLogPage(
    val entries: List<AuditEntryResponse>,
    /** Pass as `before` for the next page; `null` on the last one. */
    val nextBefore: Long?,
)

@Serializable
data class AuditEntryResponse(
    val id: Long,
    /** Epoch milliseconds. */
    val occurredAt: Long,
    val actor: AuditActorResponse,
    val action: AuditAction,
    val description: String,
)

@Serializable
data class AuditActorResponse(
    /** What to pass as `actor` to filter by this admin. */
    val subject: String,
    val name: String,
    val avatarUrl: String?,
    val kind: ActorKind,
)

@Serializable
enum class ActorKind {
    /** A Discord user with the Administrator permission. */
    DISCORD,

    /** The admin account from the environment. */
    ADMIN_ACCOUNT,
}
