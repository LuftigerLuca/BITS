package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleBlock
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.backend.rolesync.internal.RuleData
import eu.luftiger.bits.backend.rolesync.internal.RuleEvaluator
import eu.luftiger.bits.warera.WareraUser
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V16 turns every rule into a rule with exactly its one condition, V21 every office into a set of one office, V22 the
 * rules of a role into the blocks of one rule. It runs in a schema of its own, so the rules from before can be inserted.
 * The configuration is the one of [RoleSyncTest], so the context is shared.
 */
@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class RoleRuleMigrationTest {
    @Autowired
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @AfterTest
    fun dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS $SCHEMA CASCADE")
    }

    @Test
    fun `existing rules become rules with their one condition`() {
        migrate(target = "15")
        jdbc.update(
            "INSERT INTO $SCHEMA.role_rule (role_id, type, country_id, office, mu_id, threshold, target_name, created_at) VALUES " +
                "('r-citizen', 'CITIZENSHIP', 'de', NULL, NULL, NULL, 'Germany', now()), " +
                "('r-minister', 'GOVERNMENT_OFFICE', 'de', 'PRESIDENT', NULL, NULL, 'Germany', now()), " +
                "('r-veteran', 'MILITARY_UNIT', NULL, NULL, 'm1', NULL, 'Deutsche Marine', now()), " +
                "('r-veteran', 'LEVEL', NULL, NULL, NULL, 10, NULL, now())",
        )

        migrate(target = "21")

        val expected =
            listOf(
                "r-citizen" to RuleCondition(ConditionType.CITIZENSHIP, countryId = "de", targetName = "Germany"),
                "r-minister" to
                    RuleCondition(
                        ConditionType.GOVERNMENT_OFFICE,
                        countryId = "de",
                        offices = setOf(GovernmentOffice.PRESIDENT),
                        targetName = "Germany",
                    ),
                "r-veteran" to RuleCondition(ConditionType.MILITARY_UNIT, muId = "m1", targetName = "Deutsche Marine"),
                "r-veteran" to RuleCondition(ConditionType.LEVEL, threshold = 10),
            )
        assertEquals(
            expected.map { (roleId, condition) ->
                listOf(roleId, condition.key, 0, condition.type.name, condition.countryId) +
                    listOf(condition.offices.singleOrNull()?.name, condition.muId, condition.threshold, condition.targetName)
            },
            jdbc.query(
                "SELECT r.role_id, r.conditions_key, c.position, c.type, c.country_id, c.offices, c.mu_id, c.threshold, c.target_name " +
                    "FROM $SCHEMA.role_rule r JOIN $SCHEMA.role_rule_condition c ON c.rule_id = r.id ORDER BY r.id",
            ) { row, _ -> (1..9).map { row.getObject(it) } },
        )
    }

    @Test
    fun `existing office conditions keep their office as the only one, with the same key and label`() {
        migrate(target = "20")
        val ruleId =
            jdbc.queryForObject(
                "INSERT INTO $SCHEMA.role_rule (role_id, conditions_key, created_at) " +
                    "VALUES ('r-minister', 'GOVERNMENT_OFFICE|de|MINISTER_OF_DEFENSE||', now()) RETURNING id",
                Long::class.java,
            )
        jdbc.update(
            "INSERT INTO $SCHEMA.role_rule_condition (rule_id, position, type, country_id, office, target_name) " +
                "VALUES (?, 0, 'GOVERNMENT_OFFICE', 'de', 'MINISTER_OF_DEFENSE', 'Germany')",
            ruleId,
        )

        migrate(target = "21")

        val condition =
            RuleCondition(
                ConditionType.GOVERNMENT_OFFICE,
                countryId = "de",
                offices = setOf(GovernmentOffice.MINISTER_OF_DEFENSE),
                targetName = "Germany",
            )
        assertEquals("Verteidigungsminister in Germany", condition.label)
        assertEquals(
            listOf("MINISTER_OF_DEFENSE", condition.key),
            jdbc.queryForObject(
                "SELECT c.offices, r.conditions_key FROM $SCHEMA.role_rule r JOIN $SCHEMA.role_rule_condition c ON c.rule_id = r.id",
            ) { row, _ -> listOf(row.getString(1), row.getString(2)) },
        )
    }

    @Test
    fun `the rules of a role become the blocks of one rule, which gives every member the same roles`() {
        migrate(target = "21")
        // Created out of the order of their IDs, which the blocks follow.
        insertRule("r-a", "2026-01-02T00:00:00Z", listOf("CITIZENSHIP" to "de", "LEVEL" to "10"))
        insertRule("r-a", "2026-01-03T00:00:00Z", listOf("MILITARY_UNIT" to "m1"))
        insertRule("r-a", "2026-01-01T00:00:00Z", listOf("LEVEL" to "50", "MILITARY_RANK" to "20"))
        insertRule("r-b", "2026-01-01T00:00:00Z", listOf("LEVEL" to "5"))
        val before = rules(withBlocks = false)

        migrate(target = null)

        val after = rules(withBlocks = true)
        assertEquals(listOf("r-a", "r-b"), after.map { it.roleId })
        assertEquals(
            "Level ab 50 und Militärrang ab 20 oder Staatsbürgerschaft Germany und Level ab 10 oder Mitglied der MU Marine",
            after.first().label,
        )
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            jdbc.queryForList(
                "SELECT c.position FROM $SCHEMA.role_rule_condition c JOIN $SCHEMA.role_rule r ON r.id = c.rule_id " +
                    "WHERE r.role_id = 'r-a' ORDER BY c.position",
                Int::class.java,
            ),
        )
        val users =
            listOf(
                WareraUser("u1", "A", country = "de", leveling = WareraUser.Leveling(10)),
                WareraUser("u2", "B", country = "fr", mu = "m1"),
                WareraUser("u3", "C", country = "de", leveling = WareraUser.Leveling(5)),
                WareraUser("u4", "D", leveling = WareraUser.Leveling(50), militaryRank = 20),
                WareraUser("u5", "E", leveling = WareraUser.Leveling(50)),
                null,
            )

        // The old evaluator combined the rules of a role with OR, just as the new one does the blocks of one rule.
        val beforeAsBlocks =
            before.groupBy { it.roleId }.map { (roleId, rules) ->
                RoleRule(roleId, rules.flatMap { it.blocks }, Instant.EPOCH)
            }

        fun roles(rules: List<RoleRule>) =
            users.map { user ->
                RuleEvaluator(rules, RuleData()).evaluate(user)?.let { roles -> roles.grants.map { it.roleId } to roles.keepRoleIds }
            }
        assertEquals(roles(beforeAsBlocks), roles(after))
        assertEquals(listOf("r-a", "r-b"), roles(after)[0]?.first)
        assertEquals(listOf("r-b"), roles(after)[4]?.first)
    }

    private fun insertRule(
        roleId: String,
        createdAt: String,
        conditions: List<Pair<String, String>>,
    ) {
        val ruleId =
            jdbc.queryForObject(
                "INSERT INTO $SCHEMA.role_rule (role_id, conditions_key, created_at) VALUES (?, ?, ?::timestamptz) RETURNING id",
                Long::class.java,
                roleId,
                "$roleId-$createdAt",
                createdAt,
            )
        conditions.forEachIndexed { position, (type, value) ->
            when (type) {
                "CITIZENSHIP" ->
                    jdbc.update(
                        "INSERT INTO $SCHEMA.role_rule_condition (rule_id, position, type, country_id, target_name) VALUES (?, ?, ?, ?, 'Germany')",
                        ruleId,
                        position,
                        type,
                        value,
                    )
                "MILITARY_UNIT" ->
                    jdbc.update(
                        "INSERT INTO $SCHEMA.role_rule_condition (rule_id, position, type, mu_id, target_name) VALUES (?, ?, ?, ?, 'Marine')",
                        ruleId,
                        position,
                        type,
                        value,
                    )
                else ->
                    jdbc.update(
                        "INSERT INTO $SCHEMA.role_rule_condition (rule_id, position, type, threshold) VALUES (?, ?, ?, ?)",
                        ruleId,
                        position,
                        type,
                        value.toInt(),
                    )
            }
        }
    }

    /** The rules in [SCHEMA] by ID; before V22, each with one block. */
    private fun rules(withBlocks: Boolean): List<RoleRule> {
        val block = if (withBlocks) "c.block" else "0"
        val rows =
            jdbc.query(
                "SELECT r.id, r.role_id, $block, c.type, c.country_id, c.mu_id, c.threshold, c.target_name " +
                    "FROM $SCHEMA.role_rule r JOIN $SCHEMA.role_rule_condition c ON c.rule_id = r.id ORDER BY r.id, c.position",
            ) { row, _ ->
                Triple(
                    row.getLong(1) to row.getString(2),
                    row.getInt(3),
                    RuleCondition(
                        ConditionType.valueOf(row.getString(4)),
                        countryId = row.getString(5),
                        muId = row.getString(6),
                        threshold = row.getObject(7) as Int?,
                        targetName = row.getString(8),
                    ),
                )
            }
        return rows.groupBy { it.first }.map { (rule, conditions) ->
            val blocks = conditions.groupBy { it.second }.values.map { conditionsOfBlock -> RuleBlock(conditionsOfBlock.map { it.third }) }
            RoleRule(rule.second, blocks, Instant.EPOCH)
        }
    }

    private fun migrate(target: String?) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .schemas(SCHEMA)
            .locations("classpath:db/migration")
            .apply { if (target != null) target(target) }
            .load()
            .migrate()
    }

    private companion object {
        const val SCHEMA = "role_rule_migration"
    }
}
