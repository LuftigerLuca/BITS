package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.GovernmentOffice
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.internal.RuleEvaluator
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraUser
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleEvaluatorTest {
    private val german = rule("citizen", ConditionType.CITIZENSHIP, countryId = "de", targetName = "Germany")
    private val president = rule("president", ConditionType.GOVERNMENT_OFFICE, countryId = "de", office = GovernmentOffice.PRESIDENT)
    private val congress = rule("congress", ConditionType.GOVERNMENT_OFFICE, countryId = "fr", office = GovernmentOffice.CONGRESS)
    private val marine = rule("mu", ConditionType.MILITARY_UNIT, muId = "m1", targetName = "Marine")
    private val level = rule("veteran", ConditionType.LEVEL, threshold = 20)
    private val rank = rule("veteran", ConditionType.MILITARY_RANK, threshold = 50)

    private val governments =
        mapOf(
            "de" to WareraGovernment(country = "de", president = "u1"),
            "fr" to WareraGovernment(country = "fr", congressMembers = listOf("u2")),
        )

    @Test
    fun `each condition type matches its WarEra data`() {
        val evaluator = RuleEvaluator(listOf(german, president, congress, marine, level, rank), governments)

        val user = WareraUser(id = "u1", username = "A", country = "de", mu = "m1", militaryRank = 10, leveling = WareraUser.Leveling(20))
        assertEquals(
            listOf("citizen", "president", "mu", "veteran"),
            evaluator.evaluate(user)!!.grants.map { it.roleId },
        )
        val other = WareraUser(id = "u2", username = "B", country = "fr", militaryRank = 50)
        assertEquals(listOf("congress", "veteran"), evaluator.evaluate(other)!!.grants.map { it.roleId })
    }

    @Test
    fun `reason names the rule that is met`() {
        val evaluator = RuleEvaluator(listOf(level, rank), governments)

        assertEquals(
            MemberRoles(grants = listOf(RoleGrant("veteran", "BITS: Militärrang ab 50"))),
            evaluator.evaluate(WareraUser(id = "u3", username = "C", militaryRank = 60)),
        )
    }

    @Test
    fun `member who meets nothing gets nothing`() {
        assertNull(RuleEvaluator(listOf(german, level), governments).evaluate(WareraUser(id = "u3", username = "C", country = "fr")))
    }

    @Test
    fun `missing user keeps all managed roles`() {
        assertEquals(
            MemberRoles(grants = emptyList(), keepRoleIds = listOf("citizen", "veteran")),
            RuleEvaluator(listOf(german, level), governments).evaluate(null),
        )
    }

    @Test
    fun `missing government keeps the office role unless another rule decides`() {
        val evaluator = RuleEvaluator(listOf(president, rule("president", ConditionType.LEVEL, threshold = 99)), emptyMap())

        assertEquals(listOf("president"), evaluator.evaluate(WareraUser(id = "u1", username = "A"))!!.keepRoleIds)
        assertEquals(
            listOf("president"),
            evaluator.evaluate(WareraUser(id = "u1", username = "A", leveling = WareraUser.Leveling(99)))!!.grants.map { it.roleId },
        )
    }

    private fun rule(
        roleId: String,
        type: ConditionType,
        countryId: String? = null,
        office: GovernmentOffice? = null,
        muId: String? = null,
        threshold: Int? = null,
        targetName: String? = null,
    ) = RoleRule(roleId, type, countryId, office, muId, threshold, targetName, Instant.EPOCH)
}
