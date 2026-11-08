package eu.luftiger.bits.backend

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * V34 gives TOPs roles of their own, starting with the vote manager roles that decided before. It runs in a schema of
 * its own, so the roles from before can be inserted. The configuration is the one of [RoleSyncTest], so the context is
 * shared.
 */
@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class TopManagerRoleMigrationTest {
    @Autowired
    lateinit var dataSource: DataSource

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @AfterTest
    fun dropSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS $SCHEMA CASCADE")
    }

    @Test
    fun `the vote manager roles become the TOP manager roles, and the vote manager roles stay`() {
        migrate(target = "33")
        jdbc.update("INSERT INTO $SCHEMA.vote_manager_role (role_id) VALUES ('r-chair'), ('r-speaker')")

        migrate(target = "34")

        val roles = "SELECT role_id FROM $SCHEMA.%s ORDER BY role_id"
        assertEquals(listOf("r-chair", "r-speaker"), jdbc.queryForList(roles.format("top_manager_role"), String::class.java))
        assertEquals(listOf("r-chair", "r-speaker"), jdbc.queryForList(roles.format("vote_manager_role"), String::class.java))
    }

    private fun migrate(target: String) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .schemas(SCHEMA)
            .locations("classpath:db/migration")
            .target(target)
            .load()
            .migrate()
    }

    private companion object {
        const val SCHEMA = "top_manager_role_migration"
    }
}
