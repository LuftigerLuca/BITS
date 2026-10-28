package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import jakarta.servlet.ServletException
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.IllegalTransactionStateException
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class AuditLogTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var auditLog: AuditLog

    @Autowired
    lateinit var clock: MutableClock

    private val discordAdmin = SessionUser.discord("42", "Luca", "https://cdn.discordapp.com/avatars/42/a.png", Role.ADMIN)
    private val envAdmin = SessionUser.admin("admin")

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
        jdbc.execute("TRUNCATE audit_entry, role_rule, role_rule_condition, discord_role, event_publication")
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        jdbc.update(
            "INSERT INTO discord_role (id, name, color, position, assignable) VALUES (?, ?, NULL, 1, TRUE), (?, ?, NULL, 2, TRUE)",
            "r-veteran",
            "Veteran",
            "r-citizen",
            "Bürger",
        )
    }

    @Test
    fun `creating and deleting a rule records one entry each`() {
        val id = ruleId(createRule("""{"roleId":"r-citizen","blocks":[[{"type":"CITIZENSHIP","countryId":"de"}]]}""", discordAdmin))
        clock.now = Instant.parse("2026-01-01T12:05:00Z")
        mockMvc.delete("/web/admin/role-sync/rules/$id") { cookie(session(envAdmin)) }.andExpect { status { isNoContent() } }

        val log = mockMvc.get("/web/admin/audit-log") { cookie(session(envAdmin)) }
        assertEquals(listOf("ROLE_RULE_DELETED", "ROLE_RULE_CREATED"), log.read("$.entries[*].action"))
        assertEquals(
            listOf("Rolle @Bürger: Staatsbürgerschaft Germany", "Rolle @Bürger: Staatsbürgerschaft Germany"),
            log.read("$.entries[*].description"),
        )
        assertEquals(
            listOf(Instant.parse("2026-01-01T12:05:00Z").toEpochMilli(), Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()),
            log.read<List<Number>>("$.entries[*].occurredAt").map { it.toLong() },
        )
        assertEquals(listOf("admin", "Luca"), log.read("$.entries[*].actor.name"))
        assertEquals(listOf("ADMIN_ACCOUNT", "DISCORD"), log.read("$.entries[*].actor.kind"))
        assertEquals(listOf(null, discordAdmin.avatarUrl), log.read("$.entries[*].actor.avatarUrl"))
        assertEquals(null, log.read<Any?>("$.nextBefore"))
    }

    @Test
    fun `editing a rule records the rule before and after`() {
        createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""", discordAdmin)

        mockMvc
            .put("/web/admin/role-sync/rules/r-veteran") {
                cookie(session(discordAdmin))
                contentType = MediaType.APPLICATION_JSON
                content = """{"blocks":[[{"type":"LEVEL","threshold":20}],[{"type":"MILITARY_RANK","threshold":5}]]}"""
            }.andExpect { status { isOk() } }

        val log = mockMvc.get("/web/admin/audit-log") { cookie(session(discordAdmin)) }
        assertEquals(listOf("ROLE_RULE_UPDATED", "ROLE_RULE_CREATED"), log.read("$.entries[*].action"))
        assertEquals(
            "Rolle @Veteran: Level ab 20 oder Militärrang ab 5 (vorher: Level ab 10)",
            log.read<List<String>>("$.entries[*].description").first(),
        )
    }

    @Test
    fun `the description stays readable after the role is deleted in Discord`() {
        val id = ruleId(createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""", discordAdmin))
        jdbc.update("DELETE FROM discord_role WHERE id = 'r-veteran'")

        mockMvc.delete("/web/admin/role-sync/rules/$id") { cookie(session(discordAdmin)) }

        assertEquals(
            listOf("Rolle r-veteran (in Discord gelöscht): Level ab 10", "Rolle @Veteran: Level ab 10"),
            mockMvc.get("/web/admin/audit-log") { cookie(session(discordAdmin)) }.read("$.entries[*].description"),
        )
    }

    @Test
    fun `failed actions record nothing`() {
        createRule(
            """{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""",
            discordAdmin,
        ).andExpect { status { isCreated() } }

        createRule(
            """{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":0}]]}""",
            discordAdmin,
        ).andExpect { status { isBadRequest() } }
        createRule(
            """{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""",
            discordAdmin,
        ).andExpect { status { isConflict() } }
        mockMvc.delete("/web/admin/role-sync/rules/999999") { cookie(session(discordAdmin)) }.andExpect { status { isNotFound() } }

        assertEquals(1, entryCount())
    }

    @Test
    fun `a change is rolled back if its entry cannot be written`() {
        // Longer than the column for the subject allows.
        val admin = SessionUser.admin("a".repeat(300))

        // MockMvc hands the unexpected error on instead of answering with 500 as the server does.
        assertFailsWith<ServletException> {
            createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""", admin)
        }

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM role_rule", Int::class.java))
        assertEquals(0, entryCount())
    }

    @Test
    fun `entries are only written inside the transaction of a change`() {
        assertFailsWith<IllegalTransactionStateException> {
            auditLog.record(AuditActor("admin:admin", "admin", null), AuditAction.ROLE_RULE_CREATED, "Rolle @Veteran: Level ab 10")
        }
    }

    @Test
    fun `pages go from newest to oldest and combine with the filters`() {
        repeat(60) { i ->
            insertEntry(
                if (i % 2 ==
                    0
                ) {
                    "discord:42"
                } else {
                    "admin:admin"
                },
                if (i % 3 == 0) "ROLE_RULE_DELETED" else "ROLE_RULE_CREATED",
                "#$i",
            )
        }

        val first = mockMvc.get("/web/admin/audit-log") { cookie(session(discordAdmin)) }
        assertEquals((59 downTo 10).map { "#$it" }, first.read("$.entries[*].description"))
        val second =
            mockMvc.get("/web/admin/audit-log") {
                cookie(session(discordAdmin))
                param("before", first.read<Number>("$.nextBefore").toString())
            }
        assertEquals((9 downTo 0).map { "#$it" }, second.read("$.entries[*].description"))
        assertEquals(null, second.read<Any?>("$.nextBefore"))

        val filtered =
            mockMvc.get("/web/admin/audit-log") {
                cookie(session(discordAdmin))
                param("actor", "discord:42")
                param("action", "ROLE_RULE_DELETED")
            }
        assertEquals(listOf(54, 48, 42, 36, 30, 24, 18, 12, 6, 0).map { "#$it" }, filtered.read("$.entries[*].description"))
    }

    @Test
    fun `the admin filter lists everybody in the log with their latest name`() {
        insertEntry("discord:42", "ROLE_RULE_CREATED", "#0", name = "Old name")
        insertEntry("discord:42", "ROLE_RULE_CREATED", "#1", name = "Luca")
        insertEntry("admin:admin", "ROLE_RULE_DELETED", "#2", name = "admin")

        val actors = mockMvc.get("/web/admin/audit-log/actors") { cookie(session(discordAdmin)) }

        assertEquals(listOf("admin", "Luca"), actors.read("$[*].name"))
        assertEquals(listOf("admin:admin", "discord:42"), actors.read("$[*].subject"))
        assertEquals(listOf("ADMIN_ACCOUNT", "DISCORD"), actors.read("$[*].kind"))
    }

    @Test
    fun `users get 403 on the audit log`() {
        val user = session(SessionUser.discord("7", "Member", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/audit-log") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/audit-log/actors") { cookie(user) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `entries cannot be changed or deleted`() {
        insertEntry("discord:42", "ROLE_RULE_CREATED", "#0")
        val id = jdbc.queryForObject("SELECT id FROM audit_entry", Long::class.java)
        val admin = session(discordAdmin)

        mockMvc.delete("/web/admin/audit-log/$id") { cookie(admin) }.andExpect { status { is4xxClientError() } }
        mockMvc.put("/web/admin/audit-log/$id") { cookie(admin) }.andExpect { status { is4xxClientError() } }
        mockMvc.post("/web/admin/audit-log") { cookie(admin) }.andExpect { status { is4xxClientError() } }
        assertEquals(1, entryCount())
    }

    private fun createRule(
        body: String,
        user: SessionUser,
    ) = mockMvc.post("/web/admin/role-sync/rules") {
        cookie(session(user))
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun insertEntry(
        subject: String,
        action: String,
        description: String,
        name: String = subject.substringAfter(':'),
    ) {
        jdbc.update(
            "INSERT INTO audit_entry (occurred_at, actor_subject, actor_name, action, description) VALUES (now(), ?, ?, ?, ?)",
            subject,
            name,
            action,
            description,
        )
    }

    private fun entryCount() = jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry", Int::class.java)

    private fun ruleId(result: ResultActionsDsl): String = result.read("$.roleId")

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}
