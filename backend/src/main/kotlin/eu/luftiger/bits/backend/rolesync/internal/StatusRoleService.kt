package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.api.DskRoles
import eu.luftiger.bits.backend.rolesync.domain.StatusRoleSetting
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/**
 * The card "Statusrollen" of the page "Rollen": the roles every verified and every unverified member gets, so admins
 * can open channels by the verification status. Verified means linked; an open code does not count.
 *
 * A status role is neither the role of a rule nor of a DSK, which would give it by other means. A change is recorded
 * in the audit log in its transaction and syncs all members once committed. A role an admin replaces or clears stays
 * with the members who have it; BITS no longer touches it.
 */
@Service
class StatusRoleService(
    private val repository: StatusRoleRepository,
    private val lookup: StatusRoleLookup,
    private val roleRepository: DiscordRoleRepository,
    private val ruleRepository: RoleRuleRepository,
    private val dskRoles: DskRoles,
    private val syncTrigger: RoleSyncTrigger,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    fun statusRoles(): StatusRolesResponse {
        val saved = lookup.saved()
        val roles = roleRepository.findAll().associateBy { it.id }

        fun response(roleId: String?): StatusRoleResponse? {
            roleId ?: return null
            val role = roles[roleId]
            val status =
                when {
                    role == null -> RuleStatus.ORPHANED
                    !role.assignable -> RuleStatus.NOT_ASSIGNABLE
                    else -> RuleStatus.ACTIVE
                }
            return StatusRoleResponse(roleId, role?.name, role?.color, status)
        }
        return StatusRolesResponse(response(saved.verifiedRoleId), response(saved.unverifiedRoleId))
    }

    /**
     * Sets both roles, `null` for none.
     *
     * @throws ResponseStatusException 400 if both are the same role or a new one cannot be given by the bot; 409 if a new
     * one has a rule or belongs to a DSK
     */
    fun set(
        request: StatusRolesRequest,
        actor: AuditActor,
    ) {
        if (request.verifiedRoleId != null && request.verifiedRoleId == request.unverifiedRoleId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Verified and unverified must be different roles")
        }
        val before = lookup.saved()
        // A role set before may be gone in Discord meanwhile, which must not keep the admin from changing the other one.
        request.verifiedRoleId?.takeIf { it != before.verifiedRoleId }?.let(::validate)
        request.unverifiedRoleId?.takeIf { it != before.unverifiedRoleId }?.let(::validate)
        val after = SavedStatusRoles(request.verifiedRoleId, request.unverifiedRoleId)
        if (after == before) return
        transactionTemplate.executeWithoutResult {
            val setting = repository.findByIdOrNull(StatusRoleSetting.SINGLE_ROW_ID)
            if (setting == null) {
                repository.save(StatusRoleSetting(after.verifiedRoleId, after.unverifiedRoleId, clock.instant()))
            } else {
                setting.verifiedRoleId = after.verifiedRoleId
                setting.unverifiedRoleId = after.unverifiedRoleId
                setting.updatedAt = clock.instant()
            }
            auditLog.record(actor, AuditAction.STATUS_ROLES_CHANGED, "${describe(after)} (vorher: ${describe(before)})")
        }
        // A cleared role stays as it is, so only a set one needs a sync.
        if (after.ids.isNotEmpty()) syncTrigger.syncAllAfterSettingChange()
    }

    private fun validate(roleId: String) {
        val role = roleRepository.findByIdOrNull(roleId)
        if (role == null || !role.assignable) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Role $roleId cannot be assigned")
        if (ruleRepository.findByRoleId(roleId) != null) throw ResponseStatusException(HttpStatus.CONFLICT, "Role $roleId has a rule")
        if (dskRoles.all().any { it.roleId == roleId }) throw ResponseStatusException(HttpStatus.CONFLICT, "Role $roleId belongs to a DSK")
    }

    /** For the audit log, readable after a role is gone, e.g. "Verifiziert: @Verified, Unverifiziert: keine". */
    private fun describe(roles: SavedStatusRoles): String {
        val names = roleRepository.findAll().associate { it.id to it.name }

        fun label(roleId: String?) = roleId?.let { names[it]?.let { name -> "@$name" } ?: "$it (in Discord gelöscht)" } ?: "keine"
        return "Verifiziert: ${label(roles.verifiedRoleId)}, Unverifiziert: ${label(roles.unverifiedRoleId)}"
    }
}
