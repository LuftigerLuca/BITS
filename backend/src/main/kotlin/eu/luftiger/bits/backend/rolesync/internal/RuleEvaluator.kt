package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleBlock
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.warera.WareraUser

/**
 * Decides which managed roles a member gets. A block is met if all of its conditions are, and a role if any block of its
 * rule is. A condition whose WarEra data is missing is neither met nor failed, and so is a block that no other condition
 * fails: unless another block is met, the member keeps the role as it is, so an unreachable WarEra API never takes roles
 * away.
 */
class RuleEvaluator(
    /** Rules of the roles BITS manages, i.e. only those whose role exists and is assignable; one per role. */
    private val rules: List<RoleRule>,
    private val data: RuleData,
) {
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

    /** How each block of the rule of each managed role turns out for [user], in their order; [evaluate] decides on this. */
    fun check(
        user: WareraUser?,
        companyCount: Int? = null,
    ): List<RoleCheck> =
        rules.map { rule ->
            RoleCheck(
                rule.roleId,
                rule.blocks.map { block ->
                    BlockCheck(block, block.conditions.map { ConditionCheck(it, matches(it, user, companyCount)) })
                },
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

class BlockCheck(
    val block: RuleBlock,
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
    val blocks: List<BlockCheck>,
) {
    /** The first block that is met; the sync names it as the reason. */
    val metBy: RuleBlock? = blocks.firstOrNull { it.met == true }?.block

    val outcome: RoleOutcome =
        when {
            metBy != null -> RoleOutcome.MET
            blocks.any { it.met == null } -> RoleOutcome.UNDECIDED
            else -> RoleOutcome.NOT_MET
        }
}

enum class RoleOutcome {
    MET,
    NOT_MET,

    /** No block is met and the data for at least one is missing; the member keeps the role as it is. */
    UNDECIDED,
}
