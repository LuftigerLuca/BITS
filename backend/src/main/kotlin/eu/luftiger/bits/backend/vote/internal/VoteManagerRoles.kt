package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.vote.domain.VoteManagerRole
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/** Which roles, besides admins, may manage votes. */
@Service
class VoteManagerRoles(
    private val managerRoleRepository: VoteManagerRoleRepository,
    private val guildRoles: GuildRoles,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
) {
    fun mayManage(manager: VoteManager): Boolean {
        if (manager.admin) return true
        val managerRoles = managerRoleRepository.findAll().mapTo(HashSet()) { it.roleId }
        return manager.roleIds.any { it in managerRoles }
    }

    fun managerRoleIds(): List<String> = managerRoleRepository.findAll().map { it.roleId }.sorted()

    /** Only admins get here, see the controller. */
    fun setManagerRoleIds(
        roleIds: List<String>,
        actor: AuditActor,
    ) {
        val known = guildRoles.all().associateBy { it.id }
        val unknown = roleIds.filter { it !in known }
        if (unknown.isNotEmpty()) throw InvalidInputException("Unknown roles $unknown")
        transactionTemplate.executeWithoutResult {
            managerRoleRepository.deleteAllInBatch()
            managerRoleRepository.saveAll(roleIds.distinct().map(::VoteManagerRole))
            val names = roleIds.distinct().mapNotNull { known[it]?.name }.sorted()
            auditLog.record(
                actor,
                AuditAction.VOTE_MANAGER_ROLES_CHANGED,
                if (names.isEmpty()) "Abstimmungen verwalten: nur Admins" else "Abstimmungen verwalten: ${names.joinToString { "@$it" }}",
            )
        }
    }
}
