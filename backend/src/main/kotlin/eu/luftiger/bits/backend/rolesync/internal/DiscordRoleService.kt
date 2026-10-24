package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.DiscordRole
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.shared.DiscordRoleDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiscordRoleService(
    private val roleRepository: DiscordRoleRepository,
    private val ruleRepository: RoleRuleRepository,
    private val systemLog: SystemLog,
) {
    /**
     * Rules of roles missing from [roles] become orphaned; they are kept, but no longer run. A role with rules that the
     * bot could give until now and no longer can is recorded in the [SystemLog] once, not on every report.
     */
    @Transactional
    fun replaceAll(roles: List<DiscordRoleDto>) {
        val assignableBefore = roleRepository.findAssignableIds().toSet()
        val reported = roles.associateBy { it.id }
        ruleRepository
            .findAll()
            .map { it.roleId }
            .distinct()
            .filter { it in assignableBefore && reported[it]?.assignable != true }
            .forEach { roleId -> systemLog.recordProblem(SystemProblem.RoleNotAssignable(roleId, reported[roleId]?.name)) }
        roleRepository.deleteAllInBatch()
        roleRepository.saveAll(roles.map { DiscordRole(it.id, it.name, it.color, it.position, it.assignable) })
    }
}
