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
        check(user).forEach { role ->
            when (role.outcome) {
                RoleOutcome.MET -> grants += RoleGrant(role.roleId, "BITS: ${requireNotNull(role.metBy).label}")
                RoleOutcome.UNDECIDED -> keep += role.roleId
                RoleOutcome.NOT_MET -> Unit
            }
        }
        return if (grants.isEmpty() && keep.isEmpty()) null else MemberRoles(grants, keep)
    }

    /** How each rule of each managed role turns out for [user], in the order of the rules; [evaluate] decides on this. */
    fun check(user: WareraUser?): List<RoleCheck> =
        rulesByRole.map { (roleId, rules) -> RoleCheck(roleId, rules.map { RuleCheck(it, matches(it, user)) }) }

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

/** [met] is `null` if the data to decide is missing. */
class RuleCheck(
    val rule: RoleRule,
    val met: Boolean?,
)

class RoleCheck(
    val roleId: String,
    val rules: List<RuleCheck>,
) {
    /** The first rule that is met; the sync names it as the reason. */
    val metBy: RoleRule? = rules.firstOrNull { it.met == true }?.rule

    val outcome: RoleOutcome =
        when {
            metBy != null -> RoleOutcome.MET
            rules.any { it.met == null } -> RoleOutcome.UNDECIDED
            else -> RoleOutcome.NOT_MET
        }
}

enum class RoleOutcome {
    MET,
    NOT_MET,

    /** No rule is met and the data for at least one is missing; the member keeps the role as it is. */
    UNDECIDED,
}
