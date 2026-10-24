package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.GovernmentOffice
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraUser

/**
 * Decides which managed roles a member gets. A rule whose WarEra data is missing is neither met nor failed: the member
 * keeps the role as it is, so an unreachable WarEra API never takes roles away.
 */
class RuleEvaluator(
    /** Rules of the roles BITS manages, i.e. only those whose role exists and is assignable. */
    rules: List<RoleRule>,
    /** Governments by country ID; a country whose government could not be loaded is missing. */
    private val governments: Map<String, WareraGovernment>,
) {
    private val rulesByRole = rules.groupBy { it.roleId }

    /** Target roles of a linked member, or `null` if the member gets and keeps nothing. [user] is `null` if it could not be loaded. */
    fun evaluate(user: WareraUser?): MemberRoles? {
        val grants = mutableListOf<RoleGrant>()
        val keep = mutableListOf<String>()
        rulesByRole.forEach { (roleId, rules) ->
            val results = rules.map { it to matches(it, user) }
            val met = results.firstOrNull { it.second == true }
            when {
                met != null -> grants += RoleGrant(roleId, "BITS: ${met.first.label}")
                results.any { it.second == null } -> keep += roleId
            }
        }
        return if (grants.isEmpty() && keep.isEmpty()) null else MemberRoles(grants, keep)
    }

    /** `null` if the data to decide is missing. */
    private fun matches(
        rule: RoleRule,
        user: WareraUser?,
    ): Boolean? {
        if (user == null) return null
        return when (rule.type) {
            ConditionType.CITIZENSHIP -> user.country == rule.countryId
            ConditionType.GOVERNMENT_OFFICE -> governments[rule.countryId]?.let { holds(it, requireNotNull(rule.office), user.id) }
            ConditionType.MILITARY_UNIT -> user.mu == rule.muId
            ConditionType.LEVEL -> user.leveling.level >= requireNotNull(rule.threshold)
            ConditionType.MILITARY_RANK -> user.militaryRank >= requireNotNull(rule.threshold)
        }
    }

    private fun holds(
        government: WareraGovernment,
        office: GovernmentOffice,
        userId: String,
    ): Boolean =
        when (office) {
            GovernmentOffice.PRESIDENT -> government.president == userId
            GovernmentOffice.VICE_PRESIDENT -> government.vicePresident == userId
            GovernmentOffice.MINISTER_OF_ECONOMY -> government.minOfEconomy == userId
            GovernmentOffice.MINISTER_OF_DEFENSE -> government.minOfDefense == userId
            GovernmentOffice.MINISTER_OF_FOREIGN_AFFAIRS -> government.minOfForeignAffairs == userId
            GovernmentOffice.CONGRESS -> userId in government.congressMembers
        }
}
