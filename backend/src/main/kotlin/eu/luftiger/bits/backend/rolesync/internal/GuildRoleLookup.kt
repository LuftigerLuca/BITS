package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.rolesync.api.dto.GuildRole
import org.springframework.stereotype.Component

@Component
class GuildRoleLookup(
    private val roleRepository: DiscordRoleRepository,
    private val ruleInputs: RuleInputs,
) : GuildRoles {
    // The bot reports `@everyone` at position -1, every other role from 0 up.
    override fun all(): List<GuildRole> =
        roleRepository
            .findAll()
            .filter { it.position >= 0 }
            .sortedByDescending { it.position }
            .map { GuildRole(it.id, it.name, it.color) }

    override fun managedIds(): List<String> {
        val managed = ruleInputs.activeRules().mapTo(HashSet()) { it.roleId }
        return roleRepository
            .findAll()
            .filter { it.id in managed }
            .sortedByDescending { it.position }
            .map { it.id }
    }
}
