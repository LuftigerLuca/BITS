package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
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
 * V16 turns every rule into a rule with exactly its one condition. It runs in a schema of its own, so the rules from
 * before V16 can be inserted. The configuration is the one of [RoleSyncTest], so the context is shared.
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

        migrate(target = null)

        val expected =
            listOf(
                "r-citizen" to RuleCondition(ConditionType.CITIZENSHIP, countryId = "de", targetName = "Germany"),
                "r-minister" to
                    RuleCondition(
                        ConditionType.GOVERNMENT_OFFICE,
                        countryId = "de",
                        office = GovernmentOffice.PRESIDENT,
                        targetName = "Germany",
                    ),
                "r-veteran" to RuleCondition(ConditionType.MILITARY_UNIT, muId = "m1", targetName = "Deutsche Marine"),
                "r-veteran" to RuleCondition(ConditionType.LEVEL, threshold = 10),
            )
        assertEquals(
            expected.map { (roleId, condition) ->
                val rule = RoleRule(roleId, listOf(condition), Instant.EPOCH)
                listOf(roleId, rule.conditionsKey, 0, condition.type.name, condition.countryId, condition.office?.name) +
                    listOf(condition.muId, condition.threshold, condition.targetName)
            },
            jdbc.query(
                "SELECT r.role_id, r.conditions_key, c.position, c.type, c.country_id, c.office, c.mu_id, c.threshold, c.target_name " +
                    "FROM $SCHEMA.role_rule r JOIN $SCHEMA.role_rule_condition c ON c.rule_id = r.id ORDER BY r.id",
            ) { row, _ -> (1..9).map { row.getObject(it) } },
        )
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
