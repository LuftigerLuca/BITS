package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.event.internal.OutboxMonitor
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.backend.systemlog.internal.SystemLogCleanup
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.VerificationCheckedEvent
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@SpringBootTest(properties = ["bits.service-token=$SERVICE_TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class SystemLogTest {
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
    lateinit var outboxMonitor: OutboxMonitor

    @Autowired
    lateinit var cleanup: SystemLogCleanup

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        jdbc.execute(
            "TRUNCATE account_link, account_verification, role_rule, discord_role, event_publication, system_log_entry, " +
                "system_log_item, audit_entry",
        )
        redisTemplate.delete(EventStream.KEY)
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        reportRoles(
            """{"id":"r-citizen","name":"Bürger","color":null,"position":2,"assignable":true}""",
            """{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""",
        )
    }

    @Test
    fun `every sync is a run with its trigger, also without changes`() {
        syncTrigger.hourly()
        awaitBackgroundWork()
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitBackgroundWork()
        verify("1", "Luftiger", "Luca")
        awaitBackgroundWork()
        syncTrigger.hourly()
        awaitBackgroundWork()

        val log = systemLog("category" to "SYNC")
        assertEquals(listOf("HOURLY", "ACCOUNT_CHANGE", "RULE_CHANGE", "HOURLY"), log.read("$.entries[*].sync.trigger"))
        assertEquals(listOf("PENDING", "PENDING", "PENDING", "NO_RULES"), log.read("$.entries[*].sync.status"))
        assertEquals(listOf(1, 1, 0), log.read<List<Int>>("$.entries[*].sync.linkedCount").take(3))
        assertEquals(syncIds().reversed(), log.read<List<Number>>("$.entries[0:3].id").map { it.toLong() })
    }

    @Test
    fun `the run shows what the bot changed once it reports back`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitBackgroundWork()
        val syncId = syncIds().single()

        report(
            syncId,
            """
            {"memberCount":2,"staleMemberCount":0,"changes":[
              {"discordUserId":"1","memberName":"Luca","roleId":"r-veteran","roleName":"Veteran","outcome":"GRANTED"},
              {"discordUserId":"2","memberName":"Other","roleId":"r-veteran","roleName":"Veteran","outcome":"REMOVED"},
              {"discordUserId":"3","memberName":"Third","roleId":"r-veteran","roleName":"Veteran","outcome":"FAILED",
               "reason":"Discord hat die Änderung abgelehnt: Missing permissions"}
            ]}
            """,
        ).andExpect { status { isNoContent() } }
        // The bot sends it again when the answer got lost; that changes nothing.
        report(syncId, """{"memberCount":0,"staleMemberCount":0,"changes":[]}""").andExpect { status { isNoContent() } }

        val run = systemLog("category" to "SYNC")
        assertEquals(listOf("APPLIED"), run.read("$.entries[*].sync.status"))
        assertEquals(listOf(2), run.read("$.entries[*].sync.memberCount"))
        assertEquals(
            listOf(listOf(1, 1, 0, 1)),
            run.read<List<Map<String, Int>>>("$.entries[*].sync").map {
                listOf(it["granted"], it["removed"], it["skipped"], it["failed"])
            },
        )
        assertEquals(listOf(true), run.read("$.entries[*].hasItems"))
        val items = items(syncId)
        assertEquals(listOf("GRANTED", "REMOVED", "FAILED"), items.read("$[*].outcome"))
        assertEquals(listOf("Luca", "Other", "Third"), items.read("$[*].member.name"))
        assertEquals("Discord hat die Änderung abgelehnt: Missing permissions", items.read("$[2].reason"))
    }

    @Test
    fun `a run without answer is pending, then has no response, and a stale one is skipped`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitBackgroundWork()
        createRule("""{"roleId":"r-citizen","type":"LEVEL","threshold":10}""")
        awaitBackgroundWork()
        val (first, second) = syncIds()

        report(first, """{"memberCount":0,"staleMemberCount":3,"changes":[]}""")
        assertEquals(listOf("PENDING", "STALE"), systemLog().read("$.entries[*].sync.status"))
        clock.now += Duration.ofMinutes(16)
        assertEquals(listOf("NO_RESPONSE", "STALE"), systemLog().read("$.entries[*].sync.status"))
        report(second, """{"memberCount":1,"staleMemberCount":0,"changes":[]}""")
        assertEquals(listOf("APPLIED", "STALE"), systemLog().read("$.entries[*].sync.status"))
    }

    @Test
    fun `reports for unknown runs are rejected, and only with the service token`() {
        report(999_999, """{"memberCount":0,"staleMemberCount":0,"changes":[]}""").andExpect { status { isNotFound() } }
        mockMvc
            .post("/api/system-log/role-syncs/1") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"memberCount":0,"staleMemberCount":0,"changes":[]}"""
            }.andExpect { status { isUnauthorized() } }
        report(
            1,
            """{"memberCount":0,"staleMemberCount":0,"changes":[{"discordUserId":"x","memberName":"","roleId":"r",""" +
                """"roleName":null,"outcome":"GRANTED"}]}""",
        ).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `linking and relinking are recorded with member and WarEra account`() {
        verify("1", "Luftiger", "Luca")
        awaitBackgroundWork()
        verify("2", "Luftiger", "Second")
        awaitBackgroundWork()

        val log = systemLog("category" to "ACCOUNT")
        assertEquals(listOf("ACCOUNT_RELINKED", "ACCOUNT_LINKED"), log.read("$.entries[*].type"))
        assertEquals(listOf("Second", "Luca"), log.read("$.entries[*].member.name"))
        assertEquals(listOf("Luftiger", "Luftiger"), log.read("$.entries[*].wareraUsername"))
        assertEquals(mapOf("id" to "1", "name" to "Luca"), log.read("$.entries[0].previousMember"))
    }

    @Test
    fun `missing WarEra data and governments, roles the bot cannot give and lost events are errors`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        createRule("""{"roleId":"r-citizen","type":"GOVERNMENT_OFFICE","countryId":"de","office":"PRESIDENT"}""")
        verify("1", "Luftiger", "Luca")
        awaitBackgroundWork()
        warera.failingUsers += "w1"
        warera.governmentDown = true
        syncTrigger.hourly()
        awaitBackgroundWork()
        // Reported twice; the role is recorded once, when it stops being assignable.
        repeat(2) {
            reportRoles(
                """{"id":"r-citizen","name":"Bürger","color":null,"position":2,"assignable":true}""",
                """{"id":"r-veteran","name":"Veteran","color":null,"position":9,"assignable":false}""",
            )
        }
        mockMvc
            .post("/api/system-log/undelivered-events") {
                header("Authorization", "Bearer $SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = """{"streamId":"1700000000000-0","eventType":"role_sync","deliveries":5}"""
            }.andExpect { status { isNoContent() } }

        val errors = systemLog("category" to "ERROR")
        assertEquals(
            listOf("EVENT_UNDELIVERED", "ROLE_NOT_ASSIGNABLE", "WARERA_USERS_UNAVAILABLE", "GOVERNMENT_UNAVAILABLE"),
            errors.read("$.entries[*].type"),
        )
        assertEquals("Veteran", errors.read("$.entries[1].role.name"))
        val unavailable = items(errors.read<Number>("$.entries[2].id").toLong())
        // The name comes from the link recorded earlier.
        assertEquals(listOf("Luca"), unavailable.read("$[*].member.name"))
        assertEquals(
            "Die Regierung von Germany konnte nicht geladen werden. Regeln für Regierungsämter dieses Landes " +
                "wurden übersprungen.",
            errors.read("$.entries[3].message"),
        )
    }

    @Test
    fun `events stuck in the outbox are recorded once per outage`() {
        jdbc.update(
            "INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            "eu.luftiger.bits.backend.event.internal.RedisStreamWriter.write(BotEvent)",
            "BotEvent",
            "{}",
            Timestamp.from(clock.now - Duration.ofMinutes(11)),
        )

        outboxMonitor.check()
        outboxMonitor.check()
        jdbc.execute("TRUNCATE event_publication")
        outboxMonitor.check()

        assertEquals(listOf("OUTBOX_STUCK"), systemLog("category" to "ERROR").read("$.entries[*].type"))
    }

    @Test
    fun `filters by member, role and type work across runs and combine`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitBackgroundWork()
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        awaitBackgroundWork()
        verify("1", "Luftiger", "Luca")
        awaitBackgroundWork()
        val (first, second) = syncIds()
        report(first, listOf(change("1", "Luca", "r-veteran", "Veteran"), change("2", "Other", "r-veteran", "Veteran")))
        report(second, listOf(change("2", "Other", "r-citizen", "Bürger")))

        assertEquals(listOf("ACCOUNT_LINKED", "SYNC_RUN"), systemLog("member" to "luc").read("$.entries[*].type"))
        assertEquals(listOf(first), systemLog("member" to "1", "category" to "SYNC").ids())
        assertEquals(listOf(second, first), systemLog("member" to "2").ids())
        assertEquals(listOf(second), systemLog("roleId" to "r-citizen").ids())
        assertEquals(listOf<Long>(), systemLog("member" to "1", "roleId" to "r-citizen").ids())
        assertEquals(listOf("Luca"), items(first, "member" to "1").read("$[*].member.name"))
        assertEquals(
            listOf(mapOf("id" to "r-citizen", "name" to "Bürger"), mapOf("id" to "r-veteran", "name" to "Veteran")),
            mockMvc.get("/web/admin/system-log/roles") { cookie(admin) }.read("$"),
        )
    }

    @Test
    fun `pages go from newest to oldest`() {
        repeat(55) { insertEntry(clock.now) }

        val first = systemLog()
        assertEquals(50, first.read<List<Any>>("$.entries").size)
        val second = systemLog("before" to first.read<Number>("$.nextBefore").toString())
        assertEquals(5, second.read<List<Any>>("$.entries").size)
        assertEquals(null, second.read<Any?>("$.nextBefore"))
    }

    @Test
    fun `entries older than 30 days are deleted with their items`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitBackgroundWork()
        report(syncIds().single(), listOf(change("1", "Luca", "r-veteran", "Veteran")))
        insertEntry(clock.now + Duration.ofDays(25))

        clock.now += Duration.ofDays(31)
        cleanup.deleteOld()

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM system_log_entry", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_log_item", Int::class.java))
    }

    @Test
    fun `users get 403 on the system log`() {
        val user = session(SessionUser.discord("7", "Member", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/system-log") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/system-log/roles") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/system-log/1/items") { cookie(user) }.andExpect { status { isForbidden() } }
    }

    private fun change(
        discordUserId: String,
        name: String,
        roleId: String,
        roleName: String,
    ) = """{"discordUserId":"$discordUserId","memberName":"$name","roleId":"$roleId","roleName":"$roleName","outcome":"GRANTED"}"""

    private fun report(
        syncId: Long,
        changes: List<String>,
    ) = report(syncId, """{"memberCount":${changes.size},"staleMemberCount":0,"changes":[${changes.joinToString(",")}]}""")

    private fun report(
        syncId: Long,
        body: String,
    ) = mockMvc.post("/api/system-log/role-syncs/$syncId") {
        header("Authorization", "Bearer $SERVICE_TOKEN")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun systemLog(vararg params: Pair<String, String>) =
        mockMvc
            .get("/web/admin/system-log") {
                cookie(admin)
                params.forEach { (name, value) -> param(name, value) }
            }.andExpect { status { isOk() } }

    private fun items(
        entryId: Long,
        vararg params: Pair<String, String>,
    ) = mockMvc.get("/web/admin/system-log/$entryId/items") {
        cookie(admin)
        params.forEach { (name, value) -> param(name, value) }
    }

    private fun ResultActionsDsl.ids() = read<List<Number>>("$.entries[*].id").map { it.toLong() }

    private fun insertEntry(occurredAt: Instant) {
        jdbc.update(
            "INSERT INTO system_log_entry (occurred_at, kind, message) VALUES (?, 'OUTBOX_STUCK', 'test')",
            Timestamp.from(occurredAt),
        )
    }

    /** IDs of the runs the published syncs belong to, oldest first. */
    private fun syncIds() = streamEvents().filterIsInstance<RoleSyncEvent>().map { requireNotNull(it.syncId) }

    private fun createRule(body: String) =
        mockMvc
            .post("/web/admin/role-sync/rules") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isCreated() } }

    private fun reportRoles(vararg roles: String) {
        mockMvc
            .put("/api/discord-roles") {
                header("Authorization", "Bearer $SERVICE_TOKEN")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roles":[${roles.joinToString(",")}]}"""
            }.andExpect { status { isNoContent() } }
    }

    /** Links through the API with the name the bot sends along, which starts the sync of the member. */
    private fun verify(
        discordUserId: String,
        username: String,
        discordName: String,
    ) {
        val started =
            mockMvc
                .post("/api/verifications") {
                    header("Authorization", "Bearer $SERVICE_TOKEN")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"discordUserId":"$discordUserId","wareraUsername":"$username"}"""
                }.andReturn()
                .response.contentAsString
        val code = JsonPath.read<String>(started, "$.code")
        warera.companyName = code
        mockMvc.post("/api/verifications/check") {
            header("Authorization", "Bearer $SERVICE_TOKEN")
            contentType = MediaType.APPLICATION_JSON
            content = """{"discordUserId":"$discordUserId","code":"$code","discordName":"$discordName"}"""
        }
        repeat(100) {
            val result = streamEvents().filterIsInstance<VerificationCheckedEvent>().singleOrNull { it.code == code }?.result
            if (result != null) return assertEquals(CheckVerificationResponse.Linked(username), result)
            Thread.sleep(50)
        }
        fail("no result for the check of $code")
    }

    private fun awaitBackgroundWork() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
    }

    private fun streamEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}

private const val SERVICE_TOKEN = "test-service-token-at-least-32-chars"
