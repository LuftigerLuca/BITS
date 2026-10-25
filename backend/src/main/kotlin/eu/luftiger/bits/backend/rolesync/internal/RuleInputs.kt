package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraGovernment
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
     * One call per country that office rules refer to. A country whose government cannot be loaded is left out and
     * reported to [onUnavailable] with its ID and name.
     */
    fun governments(
        rules: List<RoleRule>,
        onUnavailable: (countryId: String, countryName: String?) -> Unit = { _, _ -> },
    ): Map<String, WareraGovernment> =
        rules
            .filter { it.type == ConditionType.GOVERNMENT_OFFICE && it.countryId != null }
            .distinctBy { it.countryId }
            .mapNotNull { rule ->
                val countryId = requireNotNull(rule.countryId)
                try {
                    countryId to wareraClient.getGovernment(countryId)
                } catch (e: WareraApiException) {
                    log.warn("Government of country {} could not be loaded, its office rules are skipped", countryId, e)
                    onUnavailable(countryId, rule.targetName)
                    null
                }
            }.toMap()
}
