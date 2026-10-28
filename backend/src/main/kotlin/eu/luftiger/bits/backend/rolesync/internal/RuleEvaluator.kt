package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.warera.WareraUser

/**
 * Decides which managed roles a member gets. A rule is met if all of its conditions are, and a role if any of its rules
 * is. A condition whose WarEra data is missing is neither met nor failed, and so is a rule that no other condition
 * fails: the member keeps the role as it is, so an unreachable WarEra API never takes roles away.
 */
class RuleEvaluator(
    /** Rules of the roles BITS manages, i.e. only those whose role exists and is assignable. */
    rules: List<RoleRule>,
    private val data: RuleData,
) {
    private val rulesByRole = rules.groupBy { it.roleId }

    /**
     * Target roles of a linked member, or `null` if the member gets and keeps nothing. [user] is `null` if it could not be
     * loaded, [companyCount] if the companies were not or could not be loaded.
     */
    fun evaluate(
        user: WareraUser?,
        companyCount: Int? = null,
    ): MemberRoles? {
        val grants = mutableListOf<RoleGrant>()
        val keep = mutableListOf<String>()
        check(user, companyCount).forEach { role ->
            when (role.outcome) {
                RoleOutcome.MET -> grants += RoleGrant(role.roleId, "BITS: ${requireNotNull(role.metBy).label}")
                RoleOutcome.UNDECIDED -> keep += role.roleId
                RoleOutcome.NOT_MET -> Unit
            }
        }
        return if (grants.isEmpty() && keep.isEmpty()) null else MemberRoles(grants, keep)
    }

    /** How each rule of each managed role turns out for [user], in the order of the rules; [evaluate] decides on this. */
    fun check(
        user: WareraUser?,
        companyCount: Int? = null,
    ): List<RoleCheck> =
        rulesByRole.map { (roleId, rules) ->
            RoleCheck(
                roleId,
                rules.map { rule -> RuleCheck(rule, rule.conditions.map { ConditionCheck(it, matches(it, user, companyCount)) }) },
            )
        }

    /** `null` if the data to decide is missing. */
    private fun matches(
        condition: RuleCondition,
        user: WareraUser?,
        companyCount: Int?,
    ): Boolean? {
        if (user == null) return null
        return when (condition.type) {
            ConditionType.CITIZENSHIP -> user.country == condition.countryId
            ConditionType.GOVERNMENT_OFFICE ->
                data.governments[condition.countryId]?.let { government -> condition.offices.any { it.isHeldBy(government, user.id) } }
            ConditionType.MILITARY_UNIT -> user.mu == condition.muId
            ConditionType.LEVEL -> user.leveling.level >= requireNotNull(condition.threshold)
            ConditionType.MILITARY_RANK -> user.militaryRank >= requireNotNull(condition.threshold)
            ConditionType.COMPANIES -> companyCount?.let { it >= requireNotNull(condition.threshold) }
            ConditionType.PARTY ->
                data.parties[condition.countryId]?.let { parties -> parties.any { it.id == condition.partyId && user.id in it.members } }
            ConditionType.MU_LEADERSHIP -> {
                // Without a unit of the rule, the member can only lead their own.
                val muId = condition.muId ?: user.mu ?: return false
                data.militaryUnits[muId]?.let { requireNotNull(condition.muRole).isHeldBy(it, user.id) }
            }
            ConditionType.PARTY_LEADER ->
                data.parties[condition.countryId]?.let { parties ->
                    parties.any { (condition.partyId == null || it.id == condition.partyId) && it.leader == user.id }
                }
        }
    }
}

/** [met] is `null` if the data to decide is missing. */
class ConditionCheck(
    val condition: RuleCondition,
    val met: Boolean?,
)

class RuleCheck(
    val rule: RoleRule,
    val conditions: List<ConditionCheck>,
) {
    /** `false` if any condition fails, else `null` if the data for any is missing. */
    val met: Boolean? =
        when {
            conditions.any { it.met == false } -> false
            conditions.any { it.met == null } -> null
            else -> true
        }
}

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
