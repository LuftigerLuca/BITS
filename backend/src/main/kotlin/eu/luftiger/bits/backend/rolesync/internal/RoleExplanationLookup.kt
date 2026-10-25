package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.api.RoleExplanations
import eu.luftiger.bits.backend.rolesync.api.dto.ExplainedRole
import eu.luftiger.bits.backend.rolesync.api.dto.MemberRoleExplanation
import eu.luftiger.bits.shared.RoleExplanationResponse
import org.springframework.stereotype.Component

/** Adds the names of the roles, which the bot looks up itself for `/rollen`. */
@Component
class RoleExplanationLookup(
    private val explanationService: RoleExplanationService,
    private val roleRepository: DiscordRoleRepository,
) : RoleExplanations {
    override fun explain(discordUserId: String): MemberRoleExplanation? =
        when (val response = explanationService.explain(discordUserId)) {
            RoleExplanationResponse.NotLinked -> null
            is RoleExplanationResponse.Explained -> {
                val roles = roleRepository.findAllById(response.roles.map { it.roleId }).associateBy { it.id }
                MemberRoleExplanation(
                    wareraUsername = response.wareraUsername,
                    // Only active rules are explained, and those have a role that exists.
                    roles =
                        response.roles.mapNotNull { explanation ->
                            roles[explanation.roleId]?.let { ExplainedRole(it.name, it.color, explanation) }
                        },
                )
            }
        }
}
