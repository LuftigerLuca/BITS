package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.shared.Language
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
 * Explains to a member which managed roles they meet and why, for `/roles`, the roles of DSKs included. It judges with
 * the [RuleEvaluator] of the role sync, but always with a fresh WarEra user and companies (the governments, parties and
 * military units may be cached for a few minutes), and changes nothing: the next sync brings the roles in line.
 *
 * The status roles come along as well: a linked member meets the one for verified members and not the other.
 */
@Service
class RoleExplanationService(
    private val ruleInputs: RuleInputs,
    private val statusRoles: StatusRoleLookup,
    private val roleRepository: DiscordRoleRepository,
    private val accountLinks: AccountLinks,
    private val wareraClient: WareraClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** @throws eu.luftiger.bits.warera.WareraApiException if the user cannot be loaded */
    fun explain(
        discordUserId: String,
        language: Language = Language.GERMAN,
    ): RoleExplanationResponse {
        val link = accountLinks.findByDiscordUserId(discordUserId) ?: return RoleExplanationResponse.NotLinked
        val rules = ruleInputs.activeRules()
        val dsks = ruleInputs.activeDskRoles()
        val positions = roleRepository.findAll().associate { it.id to it.position }
        val status = statusExplanations(language)
        if (rules.isEmpty() && dsks.isEmpty()) {
            return RoleExplanationResponse.Explained(link.wareraUsername, status.sortedByDescending { positions[it.roleId] })
        }
        val user = wareraClient.getUser(link.wareraUserId)
        val companyCount =
            if (rules.any { rule -> rule.conditions.any { it.type == ConditionType.COMPANIES } }) {
                companyCount(user.id)
            } else {
                null
            }
        val evaluator = RuleEvaluator(rules, ruleInputs.ruleData(rules, listOfNotNull(user.mu)), dsks)
        val ruleRoles =
            evaluator.check(user, companyCount).map { role ->
                ManagedRoleExplanation(
                    roleId = role.roleId,
                    outcome = outcome(role.outcome),
                    rules = role.blocks.map { RuleExplanation(it.block.label(language), it.met, detail(it, user, companyCount, language)) },
                )
            }
        val roles =
            (
                ruleRoles +
                    evaluator.checkDsks(user).map {
                        dskExplanation(it, language)
                    } + status
            ).sortedByDescending { positions[it.roleId] }
        return RoleExplanationResponse.Explained(user.username, roles)
    }

    /** For a linked member, whom only the verification is asked about. */
    private fun statusExplanations(language: Language): List<ManagedRoleExplanation> {
        val roles = statusRoles.active() ?: return emptyList()
        return listOfNotNull(
            roles.verifiedRoleId?.let {
                ManagedRoleExplanation(
                    it,
                    ManagedRoleOutcome.MET,
                    listOf(RuleExplanation(language.text("Verifiziert", "Verified"), met = true, detail = null)),
                )
            },
            roles.unverifiedRoleId?.let {
                ManagedRoleExplanation(
                    it,
                    ManagedRoleOutcome.NOT_MET,
                    listOf(
                        RuleExplanation(
                            language.text("Nicht verifiziert", "Not verified"),
                            met = false,
                            detail = language.text("du bist verifiziert", "you are verified"),
                        ),
                    ),
                )
            },
        )
    }

    /** A DSK as a single block: the military unit the member is in, or that they are in none of the DSK. */
    private fun dskExplanation(
        check: DskCheck,
        language: Language,
    ): ManagedRoleExplanation {
        val met = check.outcome == RoleOutcome.MET
        val explanation =
            if (met) {
                RuleExplanation(requireNotNull(check.label(language)), met = true, detail = null)
            } else {
                RuleExplanation(
                    language.text("Mitglied einer MU der ${check.dsk.name}", "Member of an MU of ${check.dsk.name}"),
                    met = false,
                    detail = language.text("nicht Mitglied einer MU der ${check.dsk.name}", "not a member of an MU of ${check.dsk.name}"),
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
     * What the user lacks for a block: the conditions that fail, or if none fails, those that cannot be
     * checked; `null` if the block is met.
     */
    private fun detail(
        check: BlockCheck,
        user: WareraUser,
        companyCount: Int?,
        language: Language,
    ): String? {
        val decisive = check.conditions.filter { it.met == check.met }
        val and = language.text(" und ", " and ")
        return when (check.met) {
            true -> null
            null -> decisive.joinToString(and) { unavailable(it.condition, language) }
            false -> decisive.joinToString(and) { missing(it.condition, user, companyCount, language) }
        }
    }

    /** `null` if the companies cannot be loaded; their conditions are then undecided. */
    private fun companyCount(wareraUserId: String): Int? =
        try {
            wareraClient.getCompanyIds(wareraUserId).size
        } catch (e: WareraApiException) {
            log.warn("Companies of WarEra user {} could not be loaded for /roles", wareraUserId, e)
            null
        }

    private fun unavailable(
        condition: RuleCondition,
        language: Language,
    ): String =
        when (condition.type) {
            ConditionType.GOVERNMENT_OFFICE ->
                language.text(
                    "Regierung von ${condition.targetName} gerade nicht abrufbar",
                    "government of ${condition.targetName} currently unavailable",
                )
            ConditionType.COMPANIES -> language.text("Firmen gerade nicht abrufbar", "companies currently unavailable")
            ConditionType.PARTY ->
                language.text("Partei ${condition.targetName} gerade nicht abrufbar", "party ${condition.targetName} currently unavailable")
            ConditionType.MU_LEADERSHIP ->
                language.text(
                    "${condition.targetName?.let { "MU $it" } ?: "deine MU"} gerade nicht abrufbar",
                    "${condition.targetName?.let { "MU $it" } ?: "your MU"} currently unavailable",
                )
            ConditionType.PARTY_LEADER -> language.text("Parteien gerade nicht abrufbar", "parties currently unavailable")
            // Decided by the user alone, who was loaded.
            ConditionType.CITIZENSHIP, ConditionType.MILITARY_UNIT, ConditionType.LEVEL, ConditionType.MILITARY_RANK ->
                language.text("gerade nicht prüfbar", "cannot be checked right now")
        }

    private fun missing(
        condition: RuleCondition,
        user: WareraUser,
        companyCount: Int?,
        language: Language,
    ): String =
        when (condition.type) {
            ConditionType.CITIZENSHIP ->
                language.text(
                    "keine Staatsbürgerschaft ${condition.targetName}",
                    "no ${condition.targetName} citizenship",
                )
            ConditionType.GOVERNMENT_OFFICE ->
                language.text("nicht ", "not ") + "${condition.officesLabel(language)} in ${condition.targetName}"
            ConditionType.MILITARY_UNIT ->
                language.text("nicht Mitglied der MU ${condition.targetName}", "not a member of MU ${condition.targetName}")
            ConditionType.LEVEL ->
                language.text(
                    "Level ${user.leveling.level} von ${threshold(condition)}",
                    "level ${user.leveling.level} of ${threshold(condition)}",
                )
            ConditionType.MILITARY_RANK ->
                language.text(
                    "Militärrang ${user.militaryRank} von ${threshold(condition)}",
                    "military rank ${user.militaryRank} of ${threshold(condition)}",
                )
            ConditionType.COMPANIES ->
                language.text(
                    "$companyCount von ${threshold(condition)} Firmen",
                    "$companyCount of ${threshold(condition)} companies",
                )
            ConditionType.PARTY ->
                language.text("nicht Mitglied der Partei ${condition.targetName}", "not a member of party ${condition.targetName}")
            ConditionType.MU_LEADERSHIP -> language.text("nicht ", "not ") + condition.label(language)
            ConditionType.PARTY_LEADER ->
                if (condition.partyId == null) {
                    language.text("kein Parteivorsitz in ${condition.targetName}", "no party leadership in ${condition.targetName}")
                } else {
                    language.text("nicht Vorsitz der Partei ${condition.targetName}", "not leader of party ${condition.targetName}")
                }
        }

    private fun threshold(condition: RuleCondition) = requireNotNull(condition.threshold)
}
