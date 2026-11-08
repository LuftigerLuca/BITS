package eu.luftiger.bits.backend.top.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.rolesync.api.dto.GuildRole
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.top.domain.TopManagerRole
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/** Which roles, besides admins, may close and reopen TOPs; independent of who may manage votes. */
@Service
class TopManagerRoles(
    private val managerRoleRepository: TopManagerRoleRepository,
    private val guildRoles: GuildRoles,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
) {
    /** Whether an admin, or a member of [roleIds], may close and reopen TOPs. */
    fun mayManage(
        admin: Boolean,
        roleIds: Collection<String>,
    ): Boolean {
        if (admin) return true
        val managerRoles = managerRoleRepository.findAll().mapTo(HashSet()) { it.roleId }
        return roleIds.any { it in managerRoles }
    }

    fun managerRoleIds(): List<String> = managerRoleRepository.findAll().map { it.roleId }.sorted()

    /** The roles of the guild to pick from, highest first. */
    fun roles(): List<GuildRole> = guildRoles.all()

    /**
     * Only admins get here, see the controller.
     *
     * @throws InvalidInputException if a role is unknown
     */
    fun setManagerRoleIds(
        roleIds: List<String>,
        actor: AuditActor,
    ) {
        val known = guildRoles.all().associateBy { it.id }
        val unknown = roleIds.filter { it !in known }
        if (unknown.isNotEmpty()) throw InvalidInputException("Unknown roles $unknown")
        transactionTemplate.executeWithoutResult {
            managerRoleRepository.deleteAllInBatch()
            managerRoleRepository.saveAll(roleIds.distinct().map(::TopManagerRole))
            val names = roleIds.distinct().mapNotNull { known[it]?.name }.sorted()
            auditLog.record(
                actor,
                AuditAction.TOP_MANAGER_ROLES_CHANGED,
                if (names.isEmpty()) "TOPs verwalten: nur Admins" else "TOPs verwalten: ${names.joinToString { "@$it" }}",
            )
        }
    }
}
