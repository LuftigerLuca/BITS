package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.MilitaryUnitRole
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.backend.rolesync.internal.RoleOutcome
import eu.luftiger.bits.backend.rolesync.internal.RuleData
import eu.luftiger.bits.backend.rolesync.internal.RuleEvaluator
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraMilitaryUnit
import eu.luftiger.bits.warera.WareraMilitaryUnitRoles
import eu.luftiger.bits.warera.WareraParty
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
        val evaluator = RuleEvaluator(listOf(german, president, congress, marine, level, rank), RuleData(governments))

        val user = WareraUser(id = "u1", username = "A", country = "de", mu = "m1", militaryRank = 10, leveling = WareraUser.Leveling(20))
        assertEquals(
            listOf("citizen", "president", "mu", "veteran"),
            evaluator.evaluate(user)!!.grants.map { it.roleId },
        )
        val other = WareraUser(id = "u2", username = "B", country = "fr", militaryRank = 50)
        assertEquals(listOf("congress", "veteran"), evaluator.evaluate(other)!!.grants.map { it.roleId })
    }

    @Test
    fun `companies and party match their WarEra data`() {
        val companies = rule("owner", ConditionType.COMPANIES, threshold = 2)
        val party = rule("party", ConditionType.PARTY, countryId = "de", targetName = "Partei", partyId = "p1")
        val data =
            RuleData(parties = mapOf("de" to listOf(WareraParty("p1", "Partei", members = listOf("u1")), WareraParty("p2", "Andere"))))
        val evaluator = RuleEvaluator(listOf(companies, party), data)

        assertEquals(
            listOf("owner", "party"),
            evaluator.evaluate(WareraUser(id = "u1", username = "A"), companyCount = 2)!!.grants.map { it.roleId },
        )
        assertEquals(
            MemberRoles(grants = listOf(RoleGrant("owner", "BITS: Besitz von mindestens 2 Firmen")), keepRoleIds = emptyList()),
            evaluator.evaluate(WareraUser(id = "u2", username = "B"), companyCount = 3),
        )
        assertNull(evaluator.evaluate(WareraUser(id = "u2", username = "B"), companyCount = 1))
    }

    @Test
    fun `missing companies or parties leave their roles undecided`() {
        val companies = rule("owner", ConditionType.COMPANIES, threshold = 1)
        val party = rule("party", ConditionType.PARTY, countryId = "de", targetName = "Partei", partyId = "p1")

        assertEquals(
            listOf("owner", "party"),
            RuleEvaluator(listOf(companies, party), RuleData())
                .evaluate(WareraUser(id = "u1", username = "A"), companyCount = null)!!
                .keepRoleIds,
        )
    }

    @Test
    fun `each rank of a military unit includes the ranks above it`() {
        val bundeswehr =
            WareraMilitaryUnit(
                "m1",
                "Bundeswehr",
                owner = "owner",
                roles = WareraMilitaryUnitRoles(managers = listOf("owner", "manager"), commanders = listOf("commander")),
            )
        val rules =
            MilitaryUnitRole.entries.map { role ->
                rule(role.name, ConditionType.MU_LEADERSHIP, muId = "m1", targetName = "Bundeswehr", muRole = role)
            }
        val evaluator = RuleEvaluator(rules, RuleData(militaryUnits = mapOf("m1" to bundeswehr)))

        fun grants(userId: String) = evaluator.evaluate(WareraUser(id = userId, username = userId, mu = "m1"))?.grants.orEmpty()
        assertEquals(listOf("COMMANDER", "MANAGER", "OWNER"), grants("owner").map { it.roleId })
        assertEquals(listOf("COMMANDER", "MANAGER"), grants("manager").map { it.roleId })
        assertEquals(listOf(RoleGrant("COMMANDER", "BITS: Kommandeur der MU Bundeswehr")), grants("commander"))
        assertEquals(emptyList(), grants("member"))
    }

    @Test
    fun `leadership of any military unit means leading the own one`() {
        val manager = rule("manager", ConditionType.MU_LEADERSHIP, muRole = MilitaryUnitRole.MANAGER)
        val data =
            RuleData(
                militaryUnits =
                    mapOf(
                        "m1" to WareraMilitaryUnit("m1", "Bundeswehr", owner = "u1"),
                        "m2" to WareraMilitaryUnit("m2", "Marine", roles = WareraMilitaryUnitRoles(managers = listOf("u2"))),
                    ),
            )
        val evaluator = RuleEvaluator(listOf(manager), data)

        assertEquals(
            MemberRoles(grants = listOf(RoleGrant("manager", "BITS: Manager einer MU"))),
            evaluator.evaluate(WareraUser(id = "u1", username = "A", mu = "m1")),
        )
        assertEquals(listOf("manager"), evaluator.evaluate(WareraUser(id = "u2", username = "B", mu = "m2"))!!.grants.map { it.roleId })
        // A manager of m2 who is in m1 does not count, nor does somebody without a unit.
        assertNull(evaluator.evaluate(WareraUser(id = "u2", username = "B", mu = "m1")))
        assertNull(evaluator.evaluate(WareraUser(id = "u2", username = "B")))
        // A unit that could not be loaded leaves the role as it is.
        assertEquals(listOf("manager"), evaluator.evaluate(WareraUser(id = "u3", username = "C", mu = "m9"))!!.keepRoleIds)
    }

    @Test
    fun `party leadership counts for the party or for any party of the country`() {
        val anyParty = rule("chair", ConditionType.PARTY_LEADER, countryId = "de", targetName = "Deutschland")
        val party = rule("p1-chair", ConditionType.PARTY_LEADER, countryId = "de", targetName = "Partei", partyId = "p1")
        val data =
            RuleData(
                parties =
                    mapOf(
                        "de" to
                            listOf(
                                WareraParty("p1", "Partei", members = listOf("u1", "u3"), leader = "u1"),
                                WareraParty("p2", "Andere", members = listOf("u2"), leader = "u2"),
                            ),
                    ),
            )
        val evaluator = RuleEvaluator(listOf(anyParty, party), data)

        assertEquals(
            listOf(RoleGrant("chair", "BITS: Parteivorsitz in Deutschland"), RoleGrant("p1-chair", "BITS: Vorsitz der Partei Partei")),
            evaluator.evaluate(WareraUser(id = "u1", username = "A"))!!.grants,
        )
        assertEquals(listOf("chair"), evaluator.evaluate(WareraUser(id = "u2", username = "B"))!!.grants.map { it.roleId })
        assertNull(evaluator.evaluate(WareraUser(id = "u3", username = "C")))
        assertEquals(
            listOf("chair", "p1-chair"),
            RuleEvaluator(listOf(anyParty, party), RuleData()).evaluate(WareraUser(id = "u1", username = "A"))!!.keepRoleIds,
        )
    }

    @Test
    fun `reason names the rule that is met`() {
        val evaluator = RuleEvaluator(listOf(level, rank), RuleData(governments))

        assertEquals(
            MemberRoles(grants = listOf(RoleGrant("veteran", "BITS: Militärrang ab 50"))),
            evaluator.evaluate(WareraUser(id = "u3", username = "C", militaryRank = 60)),
        )
    }

    @Test
    fun `member who meets nothing gets nothing`() {
        assertNull(
            RuleEvaluator(listOf(german, level), RuleData(governments)).evaluate(WareraUser(id = "u3", username = "C", country = "fr")),
        )
    }

    @Test
    fun `missing user keeps all managed roles`() {
        assertEquals(
            MemberRoles(grants = emptyList(), keepRoleIds = listOf("citizen", "veteran")),
            RuleEvaluator(listOf(german, level), RuleData(governments)).evaluate(null),
        )
    }

    @Test
    fun `missing government keeps the office role unless another rule decides`() {
        val evaluator = RuleEvaluator(listOf(president, rule("president", ConditionType.LEVEL, threshold = 99)), RuleData())

        assertEquals(listOf("president"), evaluator.evaluate(WareraUser(id = "u1", username = "A"))!!.keepRoleIds)
        assertEquals(
            listOf("president"),
            evaluator.evaluate(WareraUser(id = "u1", username = "A", leveling = WareraUser.Leveling(99)))!!.grants.map { it.roleId },
        )
    }

    @Test
    fun `rule with several conditions is met only if all of them are`() {
        val germanVeteran =
            RoleRule(
                "veteran",
                listOf(
                    condition(ConditionType.CITIZENSHIP, countryId = "de", targetName = "Germany"),
                    condition(ConditionType.LEVEL, threshold = 20),
                ),
                Instant.EPOCH,
            )
        val evaluator = RuleEvaluator(listOf(germanVeteran), RuleData(governments))

        assertEquals(
            MemberRoles(grants = listOf(RoleGrant("veteran", "BITS: Staatsbürgerschaft Germany und Level ab 20"))),
            evaluator.evaluate(WareraUser(id = "u1", username = "A", country = "de", leveling = WareraUser.Leveling(20))),
        )
        assertNull(evaluator.evaluate(WareraUser(id = "u1", username = "A", country = "de", leveling = WareraUser.Leveling(19))))
        assertNull(evaluator.evaluate(WareraUser(id = "u1", username = "A", country = "fr", leveling = WareraUser.Leveling(20))))
    }

    @Test
    fun `condition with missing data leaves its rule undecided unless another condition fails`() {
        val presidentVeteran =
            RoleRule(
                "president",
                listOf(
                    condition(ConditionType.GOVERNMENT_OFFICE, countryId = "de", office = GovernmentOffice.PRESIDENT),
                    condition(ConditionType.LEVEL, threshold = 20),
                ),
                Instant.EPOCH,
            )
        val evaluator = RuleEvaluator(listOf(presidentVeteran), RuleData())

        val veteran = WareraUser(id = "u1", username = "A", leveling = WareraUser.Leveling(20))
        assertEquals(MemberRoles(grants = emptyList(), keepRoleIds = listOf("president")), evaluator.evaluate(veteran))
        assertEquals(RoleOutcome.UNDECIDED, evaluator.check(veteran).single().outcome)
        assertNull(evaluator.evaluate(veteran.copy(leveling = WareraUser.Leveling(19))))
        assertEquals(
            listOf(
                null,
                false,
            ),
            evaluator.check(veteran.copy(leveling = WareraUser.Leveling(19))).single().rules.single().conditions.map {
                it.met
            },
        )
    }

    @Test
    fun `checks of the rules agree with the target roles of the sync`() {
        val evaluator =
            RuleEvaluator(
                listOf(german, president, congress, marine, level, rank),
                RuleData(
                    mapOf(
                        "de" to governments.getValue("de"),
                    ),
                ),
            )
        val users =
            listOf(
                WareraUser(id = "u1", username = "A", country = "de", mu = "m1", militaryRank = 10, leveling = WareraUser.Leveling(20)),
                WareraUser(id = "u2", username = "B", country = "fr", militaryRank = 50),
                WareraUser(id = "u3", username = "C"),
                null,
            )

        users.forEach { user ->
            val checks = evaluator.check(user)
            val roles = evaluator.evaluate(user) ?: MemberRoles(emptyList())
            assertEquals(
                roles.grants,
                checks.filter { it.outcome == RoleOutcome.MET }.map { RoleGrant(it.roleId, "BITS: ${it.metBy!!.label}") },
            )
            assertEquals(roles.keepRoleIds, checks.filter { it.outcome == RoleOutcome.UNDECIDED }.map { it.roleId })
        }
        // Without the French government, congress is undecided for everybody, unless the user is missing altogether.
        assertEquals(RoleOutcome.UNDECIDED, evaluator.check(users[1]).single { it.roleId == "congress" }.outcome)
    }

    private fun rule(
        roleId: String,
        type: ConditionType,
        countryId: String? = null,
        office: GovernmentOffice? = null,
        muId: String? = null,
        threshold: Int? = null,
        targetName: String? = null,
        partyId: String? = null,
        muRole: MilitaryUnitRole? = null,
    ) = RoleRule(roleId, listOf(condition(type, countryId, office, muId, threshold, targetName, partyId, muRole)), Instant.EPOCH)

    private fun condition(
        type: ConditionType,
        countryId: String? = null,
        office: GovernmentOffice? = null,
        muId: String? = null,
        threshold: Int? = null,
        targetName: String? = null,
        partyId: String? = null,
        muRole: MilitaryUnitRole? = null,
    ) = RuleCondition(type, countryId, office, muId, threshold, targetName, partyId, muRole)
}
