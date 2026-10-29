package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.members.api.dto.GuildMember
import eu.luftiger.bits.backend.members.internal.GuildMemberService
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncService
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
import org.springframework.test.web.servlet.put
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class MemberAdminTest {
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
    lateinit var syncService: RoleSyncService

    @Autowired
    lateinit var memberService: GuildMemberService

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.discord("9", "Admina", "https://cdn.discordapp.com/avatars/9/a.png", Role.ADMIN)) }

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        jdbc.execute(
            "TRUNCATE account_link, account_verification, role_rule, role_rule_condition, discord_role, event_publication, " +
                "audit_entry, system_log_entry, system_log_item, feature_setting, status_role_setting, role_sync_setting",
        )
        redisTemplate.delete(EventStream.KEY)
        memberService.forget()
        syncService.forget()
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        jdbc.update(
            "INSERT INTO discord_role (id, name, color, position, assignable) VALUES " +
                "('r-citizen', 'Bürger Deutschland', 16711680, 2, TRUE), ('r-veteran', 'Veteran', NULL, 1, TRUE)",
        )
        link("1", "w1")
        link("2", "w2")
        memberService.replaceAll(
            setOf("1", "2", "3"),
            listOf(
                GuildMember("1", "Luca", setOf("r-veteran")),
                GuildMember("2", "Bob", emptySet()),
                GuildMember("3", "Carla", emptySet()),
            ),
        )
    }

    /** Other tests expect every feature on. */
    @AfterTest
    fun resetFeatures() {
        awaitBackgroundWork()
        jdbc.execute("TRUNCATE feature_setting")
    }

    @Test
    fun `an admin finds a member by the Discord name, the WarEra name and the ID`() {
        assertEquals(listOf("1"), search("luc").read("$[*].discordUserId"))
        assertEquals(listOf("1"), search("LUFTIG").read("$[*].discordUserId"))
        assertEquals(listOf("2"), search("2").read("$[*].discordUserId"))

        val luca = search("Luftiger")
        assertEquals("Luca", luca.read("$[0].discordName"))
        assertEquals("Luftiger", luca.read("$[0].wareraUsername"))
        assertEquals(true, luca.read("$[0].onServer"))
        // Members without a link are found as well, by their Discord name.
        assertEquals(listOf(null), search("carla").read<List<Any?>>("$[*].wareraUsername"))
        assertEquals(emptyList<Any>(), search("  ").read("$"))
    }

    @Test
    fun `the details show the link and the latest system log entries, newest first`() {
        val sync = insertSync("HOURLY", Instant.parse("2026-01-01T11:00:00Z"))
        jdbc.update(
            "INSERT INTO system_log_item (entry_id, discord_user_id, discord_name, role_id, role_name, outcome, reason) VALUES " +
                "(?, '1', 'Luca', 'r-veteran', 'Veteran', 'GRANTED', 'BITS: Level 30'), " +
                "(?, '2', 'Bob', 'r-veteran', 'Veteran', 'REMOVED', NULL)",
            sync,
            sync,
        )
        jdbc.update(
            "INSERT INTO system_log_entry (occurred_at, kind, discord_user_id, discord_name, warera_user_id, warera_username) " +
                "VALUES ('2026-01-01T10:00:00Z', 'ACCOUNT_LINKED', '1', 'Luca', 'w1', 'Luftiger')",
        )

        val member = mockMvc.get("/web/admin/members/1") { cookie(admin) }.andExpect { status { isOk() } }

        assertEquals("Luca", member.read("$.discordName"))
        assertEquals("Luftiger", member.read("$.link.wareraUsername"))
        assertEquals(listOf("SYNC_RUN", "ACCOUNT_LINKED"), member.read("$.recentLog[*].kind"))
        assertEquals(listOf("HOURLY", null), member.read<List<Any?>>("$.recentLog[*].syncTrigger"))
        assertEquals(listOf("GRANTED", null), member.read<List<Any?>>("$.recentLog[*].outcome"))
        assertEquals("Veteran", member.read("$.recentLog[0].role"))
        // A member without a link is shown as long as they are on the guild.
        mockMvc.get("/web/admin/members/3") { cookie(admin) }.andExpect { jsonPath("$.link") { value(null) } }
        mockMvc.get("/web/admin/members/77") { cookie(admin) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `the roles show the reasons and whether the member holds them now`() {
        createRule("""{"roleId":"r-citizen","blocks":[[{"type":"CITIZENSHIP","countryId":"de"}]]}""")
        createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":50}]]}""")

        val roles = mockMvc.get("/web/admin/members/1/roles") { cookie(admin) }.andExpect { status { isOk() } }

        assertEquals(listOf("Bürger Deutschland", "Veteran"), roles.read("$.roles[*].name"))
        assertEquals(listOf("MET", "NOT_MET"), roles.read("$.roles[*].outcome"))
        assertEquals(listOf(false, true), roles.read("$.roles[*].held"))
        assertEquals(listOf("Level 30 von 50"), roles.read("$.roles[1].rules[*].detail"))
        mockMvc.get("/web/admin/members/3/roles") { cookie(admin) }.andExpect { jsonPath("$.linked") { value(false) } }
    }

    @Test
    fun `a sync of one member runs with fresh data, is logged as asked by an admin and audited`() {
        createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""")
        awaitBackgroundWork()
        val before = roleSyncEvents().size
        warera.users["w2"] = warera.users.getValue("w2").copy(leveling = warera.users.getValue("w1").leveling)

        mockMvc.post("/web/admin/members/2/sync") { cookie(admin) }.andExpect { status { isAccepted() } }

        awaitBackgroundWork()
        val sync = roleSyncEvents().drop(before).single()
        assertEquals(listOf("2"), sync.discordUserIds)
        assertEquals(
            listOf("r-veteran"),
            sync.members
                .getValue("2")
                .grants
                .map { it.roleId },
        )
        assertEquals(
            "ADMIN_REQUEST",
            jdbc.queryForObject("SELECT sync_trigger FROM system_log_entry WHERE id = ?", String::class.java, sync.syncId),
        )
        assertEquals(listOf("Abgleich von Bob angestoßen"), auditDescriptions("MEMBER_SYNC_REQUESTED"))
        mockMvc.post("/web/admin/members/77/sync") { cookie(admin) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `a sync is refused while nothing would be synced`() {
        setFeature("ROLE_SYNC", false)
        setFeature("NICKNAMES", false)

        mockMvc.post("/web/admin/members/1/sync") { cookie(admin) }.andExpect { status { isConflict() } }

        assertEquals(emptyList(), auditDescriptions("MEMBER_SYNC_REQUESTED"))
    }

    @Test
    fun `unlinking works like unverify and is audited`() {
        createRule("""{"roleId":"r-citizen","blocks":[[{"type":"CITIZENSHIP","countryId":"de"}]]}""")
        awaitBackgroundWork()
        val before = roleSyncEvents().size

        mockMvc.delete("/web/admin/members/1/link") { cookie(admin) }.andExpect { status { isNoContent() } }

        assertEquals(listOf("2"), jdbc.queryForList("SELECT discord_user_id FROM account_link", String::class.java))
        assertEquals(listOf("Verknüpfung von Luca mit Luftiger gelöst"), auditDescriptions("MEMBER_UNLINKED"))
        awaitBackgroundWork()
        val sync = roleSyncEvents().drop(before).single()
        assertEquals(listOf("1"), sync.discordUserIds)
        assertFalse("1" in sync.members)
        mockMvc.delete("/web/admin/members/1/link") { cookie(admin) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `members who are no admins get 403`() {
        val user = session(SessionUser.discord("1", "Luca", "https://cdn.discordapp.com/avatars/1/a.png", Role.USER))

        mockMvc.get("/web/admin/members?query=luca") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/members/1") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/members/1/roles") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.post("/web/admin/members/1/sync") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.delete("/web/admin/members/1/link") { cookie(user) }.andExpect { status { isForbidden() } }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM account_link", Int::class.java))
    }

    private fun search(query: String) =
        mockMvc
            .get("/web/admin/members") {
                cookie(admin)
                param("query", query)
            }.andExpect { status { isOk() } }

    private fun link(
        discordUserId: String,
        wareraUserId: String,
    ) {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, ?)",
            discordUserId,
            wareraUserId,
            warera.users.getValue(wareraUserId).username,
            Timestamp.from(clock.instant()),
        )
    }

    private fun createRule(body: String) =
        mockMvc
            .post("/web/admin/role-sync/rules") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isCreated() } }

    private fun setFeature(
        feature: String,
        enabled: Boolean,
    ) = mockMvc
        .put("/web/admin/features/$feature") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":$enabled}"""
        }.andExpect { status { isNoContent() } }

    private fun insertSync(
        trigger: String,
        occurredAt: Instant,
    ): Long =
        jdbc.queryForObject(
            "INSERT INTO system_log_entry (occurred_at, kind, sync_trigger, sync_status, reported_at) " +
                "VALUES (?, 'SYNC_RUN', ?, 'APPLIED', ?) RETURNING id",
            Long::class.java,
            Timestamp.from(occurredAt),
            trigger,
            Timestamp.from(occurredAt),
        )!!

    private fun auditDescriptions(action: String) =
        jdbc.queryForList("SELECT description FROM audit_entry WHERE action = ? ORDER BY id", String::class.java, action)

    /** Rule changes, syncs and unlinking run in the background, which publish through the registry. */
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
