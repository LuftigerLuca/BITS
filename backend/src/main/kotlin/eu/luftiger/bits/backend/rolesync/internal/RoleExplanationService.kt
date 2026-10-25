package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.shared.ManagedRoleExplanation
import eu.luftiger.bits.shared.ManagedRoleOutcome
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.RuleExplanation
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraUser
import org.springframework.stereotype.Service

/**
 * Explains to a member which managed roles they meet and why, for `/rollen`. It judges with the [RuleEvaluator] of the
 * role sync, but always with fresh WarEra data, and changes nothing: the next sync brings the roles in line.
 */
@Service
class RoleExplanationService(
    private val ruleInputs: RuleInputs,
    private val roleRepository: DiscordRoleRepository,
    private val accountLinks: AccountLinks,
    private val wareraClient: WareraClient,
) {
    /** @throws eu.luftiger.bits.warera.WareraApiException if the user cannot be loaded */
    fun explain(discordUserId: String): RoleExplanationResponse {
        val link = accountLinks.findByDiscordUserId(discordUserId) ?: return RoleExplanationResponse.NotLinked
        val rules = ruleInputs.activeRules()
        if (rules.isEmpty()) return RoleExplanationResponse.Explained(link.wareraUsername, emptyList())
        val user = wareraClient.getUser(link.wareraUserId)
        val positions = roleRepository.findAll().associate { it.id to it.position }
        val roles =
            RuleEvaluator(rules, ruleInputs.governments(rules))
                .check(user)
                .sortedByDescending { positions[it.roleId] }
                .map { role ->
                    ManagedRoleExplanation(
                        roleId = role.roleId,
                        outcome =
                            when (role.outcome) {
                                RoleOutcome.MET -> ManagedRoleOutcome.MET
                                RoleOutcome.NOT_MET -> ManagedRoleOutcome.NOT_MET
                                RoleOutcome.UNDECIDED -> ManagedRoleOutcome.UNDECIDED
                            },
                        rules = role.rules.map { RuleExplanation(it.rule.label, it.met, detail(it, user)) },
                    )
                }
        return RoleExplanationResponse.Explained(user.username, roles)
    }

    /** What the user lacks, in German; `null` if the rule is met. */
    private fun detail(
        check: RuleCheck,
        user: WareraUser,
    ): String? {
        val rule = check.rule
        return when (check.met) {
            true -> null
            null -> "Regierung von ${rule.targetName} gerade nicht abrufbar"
            false ->
                when (rule.type) {
                    ConditionType.CITIZENSHIP -> "keine Staatsbürgerschaft ${rule.targetName}"
                    ConditionType.GOVERNMENT_OFFICE -> "nicht ${rule.office?.label} in ${rule.targetName}"
                    ConditionType.MILITARY_UNIT -> "nicht Mitglied der MU ${rule.targetName}"
                    ConditionType.LEVEL -> "Level ${user.leveling.level} von ${threshold(rule)}"
                    ConditionType.MILITARY_RANK -> "Militärrang ${user.militaryRank} von ${threshold(rule)}"
                }
        }
    }

    private fun threshold(rule: RoleRule) = requireNotNull(rule.threshold)
}
