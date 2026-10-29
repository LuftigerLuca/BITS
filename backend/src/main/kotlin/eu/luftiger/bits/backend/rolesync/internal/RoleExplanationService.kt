package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.shared.ManagedRoleExplanation
import eu.luftiger.bits.shared.ManagedRoleOutcome
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.RuleExplanation
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Explains to a member which managed roles they meet and why, for `/rollen`, the roles of DSKs included. It judges with
 * the [RuleEvaluator] of the role sync, but always with a fresh WarEra user and companies (the governments, parties and
 * military units may be cached for a few minutes), and changes nothing: the next sync brings the roles in line.
 */
@Service
class RoleExplanationService(
    private val ruleInputs: RuleInputs,
    private val roleRepository: DiscordRoleRepository,
    private val accountLinks: AccountLinks,
    private val wareraClient: WareraClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** @throws eu.luftiger.bits.warera.WareraApiException if the user cannot be loaded */
    fun explain(discordUserId: String): RoleExplanationResponse {
        val link = accountLinks.findByDiscordUserId(discordUserId) ?: return RoleExplanationResponse.NotLinked
        val rules = ruleInputs.activeRules()
        val dsks = ruleInputs.activeDskRoles()
        if (rules.isEmpty() && dsks.isEmpty()) return RoleExplanationResponse.Explained(link.wareraUsername, emptyList())
        val user = wareraClient.getUser(link.wareraUserId)
        val companyCount =
            if (rules.any { rule -> rule.conditions.any { it.type == ConditionType.COMPANIES } }) {
                companyCount(user.id)
            } else {
                null
            }
        val positions = roleRepository.findAll().associate { it.id to it.position }
        val evaluator = RuleEvaluator(rules, ruleInputs.ruleData(rules, listOfNotNull(user.mu)), dsks)
        val ruleRoles =
            evaluator.check(user, companyCount).map { role ->
                ManagedRoleExplanation(
                    roleId = role.roleId,
                    outcome = outcome(role.outcome),
                    rules = role.blocks.map { RuleExplanation(it.block.label, it.met, detail(it, user, companyCount)) },
                )
            }
        val roles = (ruleRoles + evaluator.checkDsks(user).map(::dskExplanation)).sortedByDescending { positions[it.roleId] }
        return RoleExplanationResponse.Explained(user.username, roles)
    }

    /** A DSK as a single block: the military unit the member is in, or that they are in none of the DSK. */
    private fun dskExplanation(check: DskCheck): ManagedRoleExplanation {
        val met = check.outcome == RoleOutcome.MET
        val explanation =
            if (met) {
                RuleExplanation(requireNotNull(check.label), met = true, detail = null)
            } else {
                RuleExplanation(
                    "Mitglied einer MU der ${check.dsk.name}",
                    met = false,
                    detail = "nicht Mitglied einer MU der ${check.dsk.name}",
                )
            }
        return ManagedRoleExplanation(check.dsk.roleId, outcome(check.outcome), listOf(explanation))
    }

    private fun outcome(outcome: RoleOutcome) =
        when (outcome) {
            RoleOutcome.MET -> ManagedRoleOutcome.MET
            RoleOutcome.NOT_MET -> ManagedRoleOutcome.NOT_MET
            RoleOutcome.UNDECIDED -> ManagedRoleOutcome.UNDECIDED
        }

    /**
     * What the user lacks for a block, in German: the conditions that fail, or if none fails, those that cannot be
     * checked; `null` if the block is met.
     */
    private fun detail(
        check: BlockCheck,
        user: WareraUser,
        companyCount: Int?,
    ): String? {
        val decisive = check.conditions.filter { it.met == check.met }
        return when (check.met) {
            true -> null
            null -> decisive.joinToString(" und ") { unavailable(it.condition) }
            false -> decisive.joinToString(" und ") { missing(it.condition, user, companyCount) }
        }
    }

    /** `null` if the companies cannot be loaded; their conditions are then undecided. */
    private fun companyCount(wareraUserId: String): Int? =
        try {
            wareraClient.getCompanyIds(wareraUserId).size
        } catch (e: WareraApiException) {
            log.warn("Companies of WarEra user {} could not be loaded for /rollen", wareraUserId, e)
            null
        }

    private fun unavailable(condition: RuleCondition): String =
        when (condition.type) {
            ConditionType.GOVERNMENT_OFFICE -> "Regierung von ${condition.targetName} gerade nicht abrufbar"
            ConditionType.COMPANIES -> "Firmen gerade nicht abrufbar"
            ConditionType.PARTY -> "Partei ${condition.targetName} gerade nicht abrufbar"
            ConditionType.MU_LEADERSHIP -> "${condition.targetName?.let { "MU $it" } ?: "deine MU"} gerade nicht abrufbar"
            ConditionType.PARTY_LEADER -> "Parteien gerade nicht abrufbar"
            // Decided by the user alone, who was loaded.
            ConditionType.CITIZENSHIP, ConditionType.MILITARY_UNIT, ConditionType.LEVEL, ConditionType.MILITARY_RANK ->
                "gerade nicht prüfbar"
        }

    private fun missing(
        condition: RuleCondition,
        user: WareraUser,
        companyCount: Int?,
    ): String =
        when (condition.type) {
            ConditionType.CITIZENSHIP -> "keine Staatsbürgerschaft ${condition.targetName}"
            ConditionType.GOVERNMENT_OFFICE -> "nicht ${condition.officesLabel()} in ${condition.targetName}"
            ConditionType.MILITARY_UNIT -> "nicht Mitglied der MU ${condition.targetName}"
            ConditionType.LEVEL -> "Level ${user.leveling.level} von ${threshold(condition)}"
            ConditionType.MILITARY_RANK -> "Militärrang ${user.militaryRank} von ${threshold(condition)}"
            ConditionType.COMPANIES -> "$companyCount von ${threshold(condition)} Firmen"
            ConditionType.PARTY -> "nicht Mitglied der Partei ${condition.targetName}"
            ConditionType.MU_LEADERSHIP -> "nicht ${condition.label}"
            ConditionType.PARTY_LEADER ->
                if (condition.partyId == null) {
                    "kein Parteivorsitz in ${condition.targetName}"
                } else {
                    "nicht Vorsitz der Partei ${condition.targetName}"
                }
        }

    private fun threshold(condition: RuleCondition) = requireNotNull(condition.threshold)
}
