package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraParty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * What the [RuleEvaluator] judges a user against, loaded the same way for the role sync and for `/rollen`, so both
 * come to the same result.
 */
@Component
class RuleInputs(
    private val ruleRepository: RoleRuleRepository,
    private val roleRepository: DiscordRoleRepository,
    private val wareraClient: WareraClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Rules whose role exists and can be given by the bot; orphaned rules are not run. */
    fun activeRules(): List<RoleRule> {
        val assignable = roleRepository.findAll().filter { it.assignable }.mapTo(HashSet()) { it.id }
        return ruleRepository.findAll().filter { it.roleId in assignable }
    }

    /**
     * The data beyond the users' own that [rules] need, each only if a condition needs it: one call per country that
     * office or party conditions refer to. What cannot be loaded is left out and reported to [onUnavailable].
     */
    fun ruleData(
        rules: List<RoleRule>,
        onUnavailable: (SystemProblem) -> Unit = {},
    ): RuleData {
        val conditions = rules.flatMap { it.conditions }
        return RuleData(
            governments = governments(conditions, onUnavailable),
            parties = parties(conditions, onUnavailable),
        )
    }

    private fun governments(
        conditions: List<RuleCondition>,
        onUnavailable: (SystemProblem) -> Unit,
    ): Map<String, WareraGovernment> =
        conditions
            .filter { it.type == ConditionType.GOVERNMENT_OFFICE && it.countryId != null }
            .distinctBy { it.countryId }
            .mapNotNull { condition ->
                val countryId = requireNotNull(condition.countryId)
                try {
                    countryId to wareraClient.getGovernment(countryId)
                } catch (e: WareraApiException) {
                    log.warn("Government of country {} could not be loaded, its office conditions are undecided", countryId, e)
                    onUnavailable(SystemProblem.GovernmentUnavailable(countryId, condition.targetName))
                    null
                }
            }.toMap()

    private fun parties(
        conditions: List<RuleCondition>,
        onUnavailable: (SystemProblem) -> Unit,
    ): Map<String, List<WareraParty>> =
        conditions
            .filter { it.type == ConditionType.PARTY && it.countryId != null }
            .groupBy { requireNotNull(it.countryId) }
            .mapNotNull { (countryId, partyConditions) ->
                try {
                    countryId to wareraClient.getParties(countryId)
                } catch (e: WareraApiException) {
                    log.warn("Parties of country {} could not be loaded, their party conditions are undecided", countryId, e)
                    onUnavailable(SystemProblem.PartiesUnavailable(partyConditions.mapNotNull { it.targetName }.distinct()))
                    null
                }
            }.toMap()
}

/** What [RuleInputs.ruleData] loaded; what is missing could not be loaded or is not needed. */
class RuleData(
    /** Governments by country ID. */
    val governments: Map<String, WareraGovernment> = emptyMap(),
    /** Parties by country ID. */
    val parties: Map<String, List<WareraParty>> = emptyMap(),
)
