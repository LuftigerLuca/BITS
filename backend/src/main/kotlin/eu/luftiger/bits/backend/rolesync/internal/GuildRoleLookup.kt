package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.rolesync.api.dto.GuildRole
import org.springframework.stereotype.Component

@Component
class GuildRoleLookup(
    private val roleRepository: DiscordRoleRepository,
    private val ruleRepository: RoleRuleRepository,
    private val ruleInputs: RuleInputs,
    private val statusRoles: StatusRoleLookup,
) : GuildRoles {
    // The bot reports `@everyone` at position -1, every other role from 0 up.
    override fun all(): List<GuildRole> =
        roleRepository
            .findAll()
            .filter { it.position >= 0 }
            .sortedByDescending { it.position }
            .map { GuildRole(it.id, it.name, it.color) }

    override fun assignable(): List<GuildRole> =
        roleRepository
            .findAll()
            .filter { it.assignable }
            .sortedByDescending { it.position }
            .map { GuildRole(it.id, it.name, it.color) }

    override fun managedIds(): List<String> {
        val managed = ruleInputs.activeRules().mapTo(HashSet()) { it.roleId } + ruleInputs.activeDskRoles().map { it.roleId }
        return roleRepository
            .findAll()
            .filter { it.id in managed }
            .sortedByDescending { it.position }
            .map { it.id }
    }

    override fun withRuleIds(): Set<String> = ruleRepository.findAll().mapTo(HashSet()) { it.roleId }

    override fun statusIds(): Set<String> = statusRoles.saved().ids
}
