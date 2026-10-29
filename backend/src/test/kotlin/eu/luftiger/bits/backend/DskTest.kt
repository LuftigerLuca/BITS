package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncService
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.ManagedRoleExplanation
import eu.luftiger.bits.shared.ManagedRoleOutcome
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.RuleExplanation
import eu.luftiger.bits.shared.decodeEvent
import eu.luftiger.bits.warera.WareraMilitaryUnit
import eu.luftiger.bits.warera.WareraUser
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
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class DskTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var syncService: RoleSyncService

    @Autowired
    lateinit var syncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        clean()
        redisTemplate.delete(EventStream.KEY)
        syncService.forget()
        warera.reset()
        // A second German unit, and one based in France with a similar name.
        warera.militaryUnits["m2"] = WareraMilitaryUnit("m2", "Deutsche Luftwaffe", country = "de")
        warera.militaryUnits["m3"] = WareraMilitaryUnit("m3", "Deutsche Fremdenlegion", country = "fr")
        warera.users["w3"] = WareraUser("w3", "Flieger", country = "de", mu = "m2")
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        jdbc.update(
            "INSERT INTO overview_setting (id, country_id, country_name, updated_at) VALUES (1, 'de', 'Germany', now())",
        )
        reportRoles(
            """{"id":"r-dsk1","name":"DSK1","color":255,"position":5,"assignable":true}""",
            """{"id":"r-dsk2","name":"DSK2","color":null,"position":4,"assignable":true}""",
            """{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""",
            """{"id":"r-mod","name":"Moderator","color":null,"position":9,"assignable":false}""",
        )
    }

    /** Other tests share the database and expect no syncs without rules. */
    @AfterTest
    fun cleanUp() {
        awaitBackgroundWork()
        clean()
    }

    private fun clean() =
        jdbc
            .execute(
                "TRUNCATE dsk, dsk_military_unit, overview_setting, account_link, role_rule, role_rule_condition, discord_role, " +
                    "event_publication, feature_setting",
            ).also { jdbc.update("DELETE FROM audit_entry WHERE action LIKE 'DSK%'") }

    @Test
    fun `users get 403 on the DSKs`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/military-units") { cookie(user) }.andExpect { status { isForbidden() } }
        createDsk("""{"name":"DSK1"}""", user).andExpect { status { isForbidden() } }
        mockMvc
            .get("/web/admin/military-units/search") {
                cookie(user)
                param("query", "Deutsche")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `admins create, rename and delete DSKs, which the audit log records`() {
        createDsk("""{"name":" DSK1 "}""").andExpect { status { isCreated() } }
        createDsk("""{"name":"dsk1"}""").andExpect { status { isConflict() } }
        createDsk("""{"name":"  "}""").andExpect { status { isBadRequest() } }
        val id = dskId("DSK1")

        updateDsk(id, """{"name":"DSK 1"}""").andExpect { status { isNoContent() } }
        overview().andExpect {
            jsonPath("$.dsks[0].name") { value("DSK 1") }
            jsonPath("$.dsks[0].roleId") { value(null) }
        }
        deleteDsk(id).andExpect { status { isNoContent() } }
        deleteDsk(id).andExpect { status { isNotFound() } }

        assertEquals(
            listOf(
                "DSK_CREATED" to "DSK1, keine Rolle",
                "DSK_UPDATED" to "DSK 1, keine Rolle (vorher: DSK1, keine Rolle)",
                "DSK_DELETED" to "DSK 1, keine Rolle",
            ),
            auditEntries(),
        )
        // Without a role, nothing to sync.
        awaitBackgroundWork()
        assertEquals(emptyList(), roleSyncEvents())
    }

    @Test
    fun `the search shows military units of all countries with their country, and all of them can be assigned`() {
        // Without a country under "Verwaltung", which DSKs do not need.
        jdbc.execute("TRUNCATE overview_setting")
        createDsk("""{"name":"DSK1"}""")
        val id = dskId("DSK1")
        addUnit(id, "m1")

        val found = search("Deutsche").andExpect { status { isOk() } }
        assertEquals(listOf("m1", "m2", "m3"), found.read<List<String>>("$[*].id"))
        assertEquals(listOf("Germany", "Germany", "France"), found.read<List<String?>>("$[*].countryName"))
        assertEquals(listOf("DSK1", null, null), found.read<List<String?>>("$[*].dskName"))
        addUnit(id, "m3").andExpect { status { isCreated() } }
        addUnit(id, "unknown").andExpect { status { isBadRequest() } }
        assertEquals(
            listOf("Deutsche Fremdenlegion", "Deutsche Marine"),
            overview().read<List<String>>("$.dsks[0].militaryUnits[*].name"),
        )

        warera.down = true
        search("Deutsche").andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `members of a unit based abroad get the role of its DSK`() {
        warera.users["w4"] = WareraUser("w4", "Legionär", country = "de", mu = "m3")
        link("4", "w4")
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""")
        addUnit(dskId("DSK1"), "m3").andExpect { status { isCreated() } }

        val synced = awaitEvents(2).last()
        assertEquals(
            listOf(RoleGrant("r-dsk1", "BITS: Mitglied der MU Deutsche Fremdenlegion (DSK1)")),
            synced.members["4"]?.grants,
        )
    }

    @Test
    fun `a military unit belongs to one DSK at most`() {
        createDsk("""{"name":"DSK1"}""")
        createDsk("""{"name":"DSK2"}""")
        addUnit(dskId("DSK1"), "m1").andExpect { status { isCreated() } }

        addUnit(dskId("DSK2"), "m1").andExpect { status { isConflict() } }
        addUnit(dskId("DSK1"), "m1").andExpect { status { isConflict() } }
        removeUnit(dskId("DSK2"), "m1").andExpect { status { isNotFound() } }
        removeUnit(dskId("DSK1"), "m1").andExpect { status { isNoContent() } }
        addUnit(dskId("DSK2"), "m1").andExpect { status { isCreated() } }
    }

    @Test
    fun `a role belongs either to a DSK or to a rule, also through the API`() {
        createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""").andExpect { status { isCreated() } }
        createDsk("""{"name":"DSK1","roleId":"r-veteran"}""").andExpect { status { isConflict() } }
        createDsk("""{"name":"DSK1","roleId":"r-mod"}""").andExpect { status { isBadRequest() } }
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""").andExpect { status { isCreated() } }
        createDsk("""{"name":"DSK2","roleId":"r-dsk1"}""").andExpect { status { isConflict() } }
        createDsk("""{"name":"DSK2"}""")
        updateDsk(dskId("DSK2"), """{"name":"DSK2","roleId":"r-dsk1"}""").andExpect { status { isConflict() } }
        updateDsk(dskId("DSK2"), """{"name":"DSK2","roleId":"r-veteran"}""").andExpect { status { isConflict() } }

        createRule("""{"roleId":"r-dsk1","blocks":[[{"type":"LEVEL","threshold":10}]]}""").andExpect { status { isConflict() } }
        mockMvc
            .post("/web/admin/role-sync/rules/preview") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleId":"r-dsk1","blocks":[[{"type":"LEVEL","threshold":10}]]}"""
            }.andExpect { status { isConflict() } }

        // The dashboard is told which roles are taken, so it does not offer them.
        val roles = overview()
        assertEquals(listOf("r-dsk1", "r-dsk2", "r-veteran"), roles.read<List<String>>("$.roles[*].id"))
        assertEquals(listOf(false, false, true), roles.read<List<Boolean>>("$.roles[*].hasRule"))
        assertEquals(listOf(dskId("DSK1").toInt(), null, null), roles.read<List<Int?>>("$.roles[*].dskId"))
        val ruleRoles = mockMvc.get("/web/admin/role-sync/roles") { cookie(admin) }
        assertEquals(listOf("DSK1", null, null), ruleRoles.read<List<String?>>("$[*].dskName"))
    }

    @Test
    fun `linked members of the military units of a DSK get its role, everybody else loses it`() {
        link("1", "w1")
        link("2", "w2")
        link("3", "w3")
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""")
        val first = awaitEvents(1).last()
        assertEquals(listOf("r-dsk1"), first.managedRoleIds)
        assertEquals(listOf("r-dsk1"), first.confirmedRoleIds)
        assertEquals(emptyMap(), first.members)

        addUnit(dskId("DSK1"), "m1")
        addUnit(dskId("DSK1"), "m2")
        val second = awaitEvents(3).last()
        assertEquals(listOf(RoleGrant("r-dsk1", "BITS: Mitglied der MU Deutsche Marine (DSK1)")), second.members["1"]?.grants)
        assertEquals(listOf(RoleGrant("r-dsk1", "BITS: Mitglied der MU Deutsche Luftwaffe (DSK1)")), second.members["3"]?.grants)
        assertEquals(null, second.members["2"])

        removeUnit(dskId("DSK1"), "m2")
        val third = awaitEvents(4).last()
        assertEquals(setOf("1"), third.members.keys)
        assertEquals(listOf("r-dsk1"), third.confirmedRoleIds)
    }

    @Test
    fun `deleting the DSK or taking its role away takes the role from everybody once, then BITS leaves it alone`() {
        link("1", "w1")
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""")
        addUnit(dskId("DSK1"), "m1")
        awaitEvents(2)

        updateDsk(dskId("DSK1"), """{"name":"DSK1","roleId":"r-dsk2"}""")
        val moved = awaitEvents(3).last()
        assertEquals(setOf("r-dsk1", "r-dsk2"), moved.managedRoleIds.toSet())
        assertEquals(setOf("r-dsk1", "r-dsk2"), moved.confirmedRoleIds.toSet())
        assertEquals(listOf("r-dsk2"), moved.members["1"]?.grants?.map { it.roleId })

        updateDsk(dskId("DSK1"), """{"name":"DSK1"}""")
        val withoutRole = awaitEvents(4).last()
        assertEquals(listOf("r-dsk2"), withoutRole.confirmedRoleIds)
        assertEquals(listOf("r-dsk2"), withoutRole.managedRoleIds)
        assertEquals(emptyMap(), withoutRole.members)

        updateDsk(dskId("DSK1"), """{"name":"DSK1","roleId":"r-dsk1"}""")
        awaitEvents(5)
        deleteDsk(dskId("DSK1"))
        val deleted = awaitEvents(6).last()
        assertEquals(listOf("r-dsk1"), deleted.confirmedRoleIds)
        assertEquals(listOf("r-dsk1"), deleted.managedRoleIds)
        assertEquals(emptyMap(), deleted.members)

        // A rename alone syncs nothing, and the next sync no longer manages the role.
        createDsk("""{"name":"DSK2"}""")
        updateDsk(dskId("DSK2"), """{"name":"DSK 2"}""")
        awaitEvents(6)
        createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""")
        assertEquals(listOf("r-veteran"), awaitEvents(7).last().managedRoleIds)
    }

    @Test
    fun `the hourly sync follows a change of military unit, and members who cannot be loaded keep the role`() {
        link("1", "w1")
        link("3", "w3")
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""")
        addUnit(dskId("DSK1"), "m1")
        assertEquals(setOf("1"), awaitEvents(2).last().members.keys)

        warera.users["w1"] = warera.users.getValue("w1").copy(mu = "m2")
        warera.users["w3"] = warera.users.getValue("w3").copy(mu = "m1")
        syncTrigger.hourly()
        val hourly = awaitEvents(3).last()
        assertEquals(setOf("3"), hourly.members.keys)

        warera.failingUsers += "w3"
        syncTrigger.hourly()
        val failed = awaitEvents(4).last()
        assertEquals(listOf("r-dsk1"), failed.members["3"]?.keepRoleIds)
        assertEquals(emptyList(), failed.members["3"]?.grants)
    }

    @Test
    fun `while the role sync is off, DSK roles are left alone`() {
        link("1", "w1")
        setFeature("ROLE_SYNC", false)
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""")
        addUnit(dskId("DSK1"), "m1")
        awaitBackgroundWork()
        assertEquals(emptyList(), roleSyncEvents())

        setFeature("ROLE_SYNC", true)
        val event = awaitEvents(1).single()
        assertEquals(listOf("r-dsk1"), event.members["1"]?.grants?.map { it.roleId })
    }

    @Test
    fun `role explanation names the DSK`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-veteran","blocks":[[{"type":"LEVEL","threshold":10}]]}""")
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""")
        addUnit(dskId("DSK1"), "m1")

        assertEquals(
            ManagedRoleExplanation(
                "r-dsk1",
                ManagedRoleOutcome.MET,
                listOf(RuleExplanation("Mitglied der MU Deutsche Marine (DSK1)", met = true, detail = null)),
            ),
            explained("1").roles.first(),
        )
        assertEquals(listOf("r-dsk1", "r-veteran"), explained("1").roles.map { it.roleId })
        assertEquals(
            ManagedRoleExplanation(
                "r-dsk1",
                ManagedRoleOutcome.NOT_MET,
                listOf(RuleExplanation("Mitglied einer MU der DSK1", met = false, detail = "nicht Mitglied einer MU der DSK1")),
            ),
            explained("2").roles.first(),
        )
    }

    @Test
    fun `a DSK whose role was deleted in Discord is orphaned and not run`() {
        link("1", "w1")
        createDsk("""{"name":"DSK1","roleId":"r-dsk1"}""")
        addUnit(dskId("DSK1"), "m1")
        awaitEvents(2)
        reportRoles("""{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""")

        overview().andExpect { jsonPath("$.dsks[0].roleStatus") { value("ORPHANED") } }
        syncTrigger.hourly()
        awaitBackgroundWork()
        // Without rules and active DSKs, there is nothing left to sync.
        assertEquals(2, roleSyncEvents().size)
    }

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun overview() = mockMvc.get("/web/admin/military-units") { cookie(admin) }

    private fun dskId(name: String): Long =
        JsonPath
            .read<List<Number>>(overview().andReturn().response.contentAsString, "$.dsks[?(@.name == '$name')].id")
            .single()
            .toLong()

    private fun createDsk(
        body: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/military-units/dsks") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun updateDsk(
        id: Long,
        body: String,
    ): ResultActionsDsl =
        mockMvc.put("/web/admin/military-units/dsks/$id") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun deleteDsk(id: Long) = mockMvc.delete("/web/admin/military-units/dsks/$id") { cookie(admin) }

    private fun addUnit(
        dskId: Long,
        muId: String,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/military-units/dsks/$dskId/military-units") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"muId":"$muId"}"""
        }

    private fun removeUnit(
        dskId: Long,
        muId: String,
    ) = mockMvc.delete("/web/admin/military-units/dsks/$dskId/military-units/$muId") { cookie(admin) }

    private fun search(query: String) =
        mockMvc.get("/web/admin/military-units/search") {
            cookie(admin)
            param("query", query)
        }

    private fun createRule(body: String): ResultActionsDsl =
        mockMvc.post("/web/admin/role-sync/rules") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun setFeature(
        feature: String,
        enabled: Boolean,
    ) = mockMvc
        .put("/web/admin/features/$feature") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":$enabled}"""
        }.andExpect { status { isNoContent() } }

    private fun explained(discordUserId: String): RoleExplanationResponse.Explained =
        BitsJson.decodeFromString<RoleExplanationResponse>(
            mockMvc
                .post("/api/role-explanations") {
                    header("Authorization", "Bearer test-service-token-at-least-32-chars")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"discordUserId":"$discordUserId"}"""
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        ) as RoleExplanationResponse.Explained

    private fun auditEntries(): List<Pair<String, String>> =
        jdbc.query("SELECT action, description FROM audit_entry WHERE action LIKE 'DSK%' ORDER BY id") { rs, _ ->
            rs.getString("action") to rs.getString("description")
        }

    private fun reportRoles(vararg roles: String) {
        mockMvc
            .put("/api/discord-roles") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roles":[${roles.joinToString(",")}]}"""
            }.andExpect { status { isNoContent() } }
    }

    private fun link(
        discordUserId: String,
        wareraUserId: String,
    ) {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, now())",
            discordUserId,
            wareraUserId,
            warera.users.getValue(wareraUserId).username,
        )
    }

    /** Syncs run in the background, one after the other; waits for them and expects exactly [count] role syncs. */
    private fun awaitEvents(count: Int): List<RoleSyncEvent> {
        awaitBackgroundWork()
        return roleSyncEvents().also { assertEquals(count, it.size, "expected $count role syncs, got $it") }
    }

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

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }
}
