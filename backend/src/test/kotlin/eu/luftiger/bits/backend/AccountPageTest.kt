package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.decodeEvent
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class AccountPageTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    private val member = SessionUser.discord("1", "Luca", "https://cdn.discordapp.com/avatars/1/a.png", Role.USER)
    private val other = SessionUser.discord("2", "Bob", "https://cdn.discordapp.com/avatars/2/b.png", Role.ADMIN)

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        jdbc.execute(
            "TRUNCATE account_link, account_verification, nickname_opt_in, role_rule, discord_role, event_publication, ping_log, " +
                "audit_entry, system_log_entry, system_log_item",
        )
        redisTemplate.delete(EventStream.KEY)
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        jdbc.update(
            "INSERT INTO discord_role (id, name, color, position, assignable) VALUES (?, ?, 16711680, 2, TRUE), (?, ?, NULL, 1, TRUE)",
            "r-citizen",
            "Bürger Deutschland",
            "r-veteran",
            "Veteran",
        )
    }

    @Test
    fun `a member sees their link and the last sync of all members`() {
        link("1", "w1", Instant.parse("2025-12-24T18:00:00Z"))
        val hourly = Instant.parse("2026-01-01T11:00:30Z")
        insertSync("HOURLY", "APPLIED", hourly)
        // Neither a sync of single members, nor one the bot has not applied yet, counts.
        insertSync("ACCOUNT_CHANGE", "APPLIED", Instant.parse("2026-01-01T11:30:00Z"))
        insertSync("RULE_CHANGE", "PENDING", null)

        val account = mockMvc.get("/web/account") { cookie(session(member)) }.andExpect { status { isOk() } }

        assertEquals("Luftiger", account.read("$.link.wareraUsername"))
        assertEquals(Instant.parse("2025-12-24T18:00:00Z").toEpochMilli(), account.read<Number>("$.link.verifiedAt").toLong())
        assertEquals(hourly.toEpochMilli(), account.read<Number>("$.lastSyncAt").toLong())
    }

    @Test
    fun `a member without link sees no link and no roles`() {
        mockMvc.get("/web/account") { cookie(session(member)) }.andExpect {
            jsonPath("$.link") { value(null) }
            jsonPath("$.lastSyncAt") { value(null) }
        }
        mockMvc.get("/web/account/roles") { cookie(session(member)) }.andExpect {
            jsonPath("$.linked") { value(false) }
            jsonPath("$.roles") { isEmpty() }
        }
    }

    @Test
    fun `a member sees their managed roles with the reasons`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":50}""")

        val roles = mockMvc.get("/web/account/roles") { cookie(session(member)) }.andExpect { status { isOk() } }

        assertEquals(true, roles.read("$.linked"))
        assertEquals(listOf("Bürger Deutschland", "Veteran"), roles.read("$.roles[*].name"))
        assertEquals(listOf(16711680, null), roles.read("$.roles[*].color"))
        assertEquals(listOf("MET", "NOT_MET"), roles.read("$.roles[*].outcome"))
        assertEquals(listOf("Staatsbürgerschaft Germany"), roles.read("$.roles[0].rules[*].label"))
        assertEquals(listOf("Level 30 von 50"), roles.read("$.roles[1].rules[*].detail"))
    }

    @Test
    fun `roles are a 503 if WarEra cannot be reached`() {
        link("1", "w1")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        warera.down = true

        mockMvc.get("/web/account/roles") { cookie(session(member)) }.andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `unlinking deletes the link and the open code, and the next sync takes the managed roles`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        jdbc.update(
            "INSERT INTO account_verification (discord_user_id, warera_user_id, warera_username, code, expires_at) " +
                "VALUES ('1', 'w2', 'Other', 'BITS-ABC234', now() + interval '10 minutes')",
        )
        awaitBackgroundWork()
        val before = roleSyncEvents().size

        mockMvc.delete("/web/account/link") { cookie(session(member)) }.andExpect { status { isNoContent() } }

        assertEquals(listOf("2"), jdbc.queryForList("SELECT discord_user_id FROM account_link", String::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM account_verification", Int::class.java))
        awaitBackgroundWork()
        val sync = roleSyncEvents().drop(before).single()
        assertEquals(listOf("1"), sync.discordUserIds)
        assertEquals(listOf("r-citizen"), sync.managedRoleIds)
        assertFalse("1" in sync.members)
        mockMvc.delete("/web/account/link") { cookie(session(member)) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `the download holds what BITS keeps about the member and nothing about others`() {
        link("1", "w1", Instant.parse("2025-12-24T18:00:00Z"))
        jdbc.update("INSERT INTO nickname_opt_in (discord_user_id, opted_in_at) VALUES ('1', '2025-12-25T10:00:00Z')")
        jdbc.update("INSERT INTO ping_log (discord_user_id, created_at) VALUES ('1', '2025-12-30T09:00:00Z'), ('2', now())")
        // Bob took the WarEra account w2 from the member, then the member linked w1.
        insertEntry("ACCOUNT_LINKED", "1", "Luca", null, null, "w2", "Other")
        insertEntry("ACCOUNT_RELINKED", "2", "Bob", "1", "Luca", "w2", "Other")
        val sync = insertSync("HOURLY", "APPLIED", Instant.parse("2026-01-01T11:00:00Z"))
        insertItem(sync, "1", "Luca", "r-citizen", "GRANTED", "BITS: Staatsbürgerschaft Germany")
        insertItem(sync, "2", "Bob", "r-citizen", "REMOVED", null)
        jdbc.update(
            "INSERT INTO audit_entry (occurred_at, actor_subject, actor_name, actor_avatar_url, action, description) VALUES " +
                "('2025-12-31T10:00:00Z', 'discord:1', 'Luca', NULL, 'OVERVIEW_COUNTRY_SET', 'Land auf Germany gesetzt'), " +
                "('2025-12-31T11:00:00Z', 'discord:2', 'Bob', NULL, 'ROLE_RULE_DELETED', 'Rolle @Bürger: Staatsbürgerschaft Germany')",
        )

        // A Discord ID in the request is not looked at.
        val export =
            mockMvc
                .get("/web/account/export?discordUserId=2") { cookie(session(member)) }
                .andExpect {
                    status { isOk() }
                    content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                    header { string("Content-Disposition", "attachment; filename=\"bits-daten-1.json\"") }
                }

        assertEquals("1", export.read("$.discordUserId"))
        assertEquals("2026-01-01T12:00:00Z", export.read("$.exportedAt"))
        assertEquals("Luftiger", export.read("$.link.wareraUsername"))
        assertEquals("2025-12-24T18:00:00Z", export.read("$.link.verifiedAt"))
        assertEquals(null, export.read<Any?>("$.openVerification"))
        assertEquals("2025-12-25T10:00:00Z", export.read("$.nicknameOptedInAt"))
        assertEquals(listOf("2025-12-30T09:00:00Z"), export.read("$.pings"))
        assertEquals(listOf("ACCOUNT_LINKED", "ACCOUNT_RELINKED"), export.read("$.systemLog.entries[*].kind"))
        assertEquals(listOf("Luca", "Luca"), export.read("$.systemLog.entries[*].discordName"))
        assertEquals(listOf(false, true), export.read("$.systemLog.entries[*].previousAccount"))
        assertEquals(listOf("GRANTED"), export.read("$.systemLog.items[*].outcome"))
        assertEquals(listOf("HOURLY"), export.read("$.systemLog.items[*].syncTrigger"))
        assertEquals(listOf("OVERVIEW_COUNTRY_SET"), export.read("$.auditLog[*].action"))
        val body = export.andReturn().response.contentAsString
        listOf("\"2\"", "Bob", "REMOVED", "Rolle @Bürger").forEach { assertFalse(it in body, "export contains $it") }
    }

    @Test
    fun `the download of a member without data is empty but complete`() {
        val export = mockMvc.get("/web/account/export") { cookie(session(member)) }.andExpect { status { isOk() } }

        assertEquals(null, export.read<Any?>("$.link"))
        assertEquals(emptyList<Any>(), export.read("$.pings"))
        assertEquals(emptyList<Any>(), export.read("$.systemLog.entries"))
        assertEquals(emptyList<Any>(), export.read("$.auditLog"))
    }

    @Test
    fun `the admin account without Discord gets 403, and nobody gets in without a session`() {
        val envAdmin = session(SessionUser.admin("admin"))

        listOf("/web/account", "/web/account/roles", "/web/account/export").forEach { path ->
            mockMvc.get(path) { cookie(envAdmin) }.andExpect { status { isForbidden() } }
            mockMvc.get(path).andExpect { status { isUnauthorized() } }
        }
        mockMvc.delete("/web/account/link") { cookie(envAdmin) }.andExpect { status { isForbidden() } }
        mockMvc.delete("/web/account/link").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `unlinking from another site is rejected`() {
        link("1", "w1")

        mockMvc
            .delete("/web/account/link") {
                cookie(session(member))
                header("Sec-Fetch-Site", "same-site")
                header("Origin", "https://evil.bits.test")
            }.andExpect { status { isForbidden() } }

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM account_link", Int::class.java))
    }

    private fun link(
        discordUserId: String,
        wareraUserId: String,
        verifiedAt: Instant = clock.instant(),
    ) {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, ?)",
            discordUserId,
            wareraUserId,
            warera.users.getValue(wareraUserId).username,
            Timestamp.from(verifiedAt),
        )
    }

    private fun createRule(body: String) =
        mockMvc
            .post("/web/admin/role-sync/rules") {
                cookie(session(other))
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isCreated() } }

    private fun insertSync(
        trigger: String,
        status: String,
        reportedAt: Instant?,
    ): Long =
        jdbc.queryForObject(
            "INSERT INTO system_log_entry (occurred_at, kind, sync_trigger, sync_status, reported_at) " +
                "VALUES (?, 'SYNC_RUN', ?, ?, ?) RETURNING id",
            Long::class.java,
            Timestamp.from(reportedAt ?: clock.instant()),
            trigger,
            status,
            reportedAt?.let(Timestamp::from),
        )!!

    private fun insertEntry(
        kind: String,
        discordUserId: String,
        discordName: String,
        previousDiscordUserId: String?,
        previousDiscordName: String?,
        wareraUserId: String,
        wareraUsername: String,
    ) {
        jdbc.update(
            "INSERT INTO system_log_entry (occurred_at, kind, discord_user_id, discord_name, previous_discord_user_id, " +
                "previous_discord_name, warera_user_id, warera_username) VALUES (now(), ?, ?, ?, ?, ?, ?, ?)",
            kind,
            discordUserId,
            discordName,
            previousDiscordUserId,
            previousDiscordName,
            wareraUserId,
            wareraUsername,
        )
    }

    private fun insertItem(
        entryId: Long,
        discordUserId: String,
        discordName: String,
        roleId: String,
        outcome: String,
        reason: String?,
    ) {
        jdbc.update(
            "INSERT INTO system_log_item (entry_id, discord_user_id, discord_name, role_id, role_name, outcome, reason) " +
                "VALUES (?, ?, ?, ?, 'Bürger Deutschland', ?, ?)",
            entryId,
            discordUserId,
            discordName,
            roleId,
            outcome,
            reason,
        )
    }

    /** Rule changes and unlinking start syncs in the background, which publish through the registry. */
    private fun awaitBackgroundWork() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
    }

    private fun roleSyncEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }
            .filterIsInstance<RoleSyncEvent>()

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}
