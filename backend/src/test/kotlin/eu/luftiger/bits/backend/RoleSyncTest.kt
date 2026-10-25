package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.members.internal.GuildMemberService
import eu.luftiger.bits.backend.rolesync.internal.PreviewMember
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncService
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.backend.rolesync.internal.RulePreviewResponse
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.DiscordMembersReport
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.ManagedRoleExplanation
import eu.luftiger.bits.shared.ManagedRoleOutcome
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.ReportedMember
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.RuleExplanation
import eu.luftiger.bits.shared.VerificationCheckedEvent
import eu.luftiger.bits.shared.decodeEvent
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraBadRequestException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCompany
import eu.luftiger.bits.warera.WareraCountry
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraMilitaryUnit
import eu.luftiger.bits.warera.WareraNotFoundException
import eu.luftiger.bits.warera.WareraSchemaException
import eu.luftiger.bits.warera.WareraUser
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataIntegrityViolationException
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class RoleSyncTest {
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
    lateinit var warera: FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var memberService: GuildMemberService

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        jdbc.execute(
            "TRUNCATE account_link, account_verification, role_rule, discord_role, event_publication, nickname_opt_in",
        )
        redisTemplate.delete(EventStream.KEY)
        memberService.forget()
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        reportRoles(
            """{"id":"r-citizen","name":"Bürger Deutschland","color":16711680,"position":3,"assignable":true}""",
            """{"id":"r-minister","name":"Minister","color":null,"position":2,"assignable":true}""",
            """{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""",
            """{"id":"r-mod","name":"Moderator","color":null,"position":9,"assignable":false}""",
        )
    }

    /** Other tests share the database and expect no syncs without rules. */
    @AfterTest
    fun deleteNicknameOptIns() {
        awaitBackgroundWork()
        jdbc.execute("TRUNCATE nickname_opt_in")
    }

    @Test
    fun `users get 403 on the rules`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/role-sync/rules") { cookie(user) }.andExpect { status { isForbidden() } }
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""", user).andExpect { status { isForbidden() } }
        previewRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""", user).andExpect { status { isForbidden() } }
        mockMvc.get("/web/admin/role-sync/rules/1/deletion-preview") { cookie(user) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `preview of a new rule agrees with the sync that follows`() {
        warera.users["w4"] = WareraUser("w4", "Failing", leveling = WareraUser.Leveling(50))
        warera.users["w5"] = WareraUser("w5", "Late", leveling = WareraUser.Leveling(1))
        link("1", "w1")
        link("2", "w2")
        link("4", "w4")
        warera.failingUsers += "w4"
        syncTrigger.hourly()
        awaitBackgroundWork()
        // Linked after the hourly refresh, so the sync loads them first.
        link("5", "w5")
        val holders =
            mapOf(
                "1" to emptyList(),
                "2" to listOf("r-veteran"),
                // Given by hand, without a link.
                "3" to listOf("r-veteran", "r-citizen"),
                "4" to listOf("r-veteran"),
                "5" to emptyList(),
            )
        reportMembers(holders)

        val preview = preview("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")

        assertEquals(true, preview.managed)
        assertEquals(true, preview.membersReported)
        assertEquals(true, preview.usersLoaded)
        assertEquals(listOf(PreviewMember("1", "Member 1")), preview.gains)
        assertEquals(listOf("2", "3"), preview.losses.map { it.discordUserId })
        assertEquals(listOf("4", "5"), preview.unknown.map { it.discordUserId })

        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        val event = awaitEvents(1).single() as RoleSyncEvent
        val (gains, losses) = changesOf(event, holders, "r-veteran")
        assertEquals(preview.gains.map { it.discordUserId }.toSet(), gains)
        assertEquals(preview.losses.map { it.discordUserId }.toSet(), losses)
    }

    @Test
    fun `preview of a deletion counts the members of the rules that are left`() {
        link("1", "w1")
        link("2", "w2")
        val citizen = ruleId(createRule("""{"roleId":"r-veteran","type":"CITIZENSHIP","countryId":"de"}"""))
        val level = ruleId(createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":2}"""))
        awaitEvents(2)
        reportMembers(mapOf("1" to listOf("r-veteran"), "2" to listOf("r-veteran")))

        val withoutLevel = deletionPreview(level)
        assertEquals(true, withoutLevel.managed)
        assertEquals(listOf("2"), withoutLevel.losses.map { it.discordUserId })
        assertEquals(emptyList(), withoutLevel.gains)

        mockMvc.delete("/web/admin/role-sync/rules/$citizen") { cookie(admin) }
        awaitEvents(3)
        // The last rule: BITS no longer manages the role, so nobody loses it.
        val withoutAny = deletionPreview(level)
        assertEquals(false, withoutAny.managed)
        assertEquals(emptyList(), withoutAny.losses)
        mockMvc.get("/web/admin/role-sync/rules/999999/deletion-preview") { cookie(admin) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `preview saves, records and publishes nothing`() {
        link("1", "w1")
        reportMembers(mapOf("1" to emptyList()))
        val audits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry", Int::class.java)

        preview("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        previewRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":0}""").andExpect { status { isBadRequest() } }

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM role_rule", Int::class.java))
        assertEquals(audits, jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry", Int::class.java))
        awaitEvents(0)
    }

    @Test
    fun `preview sends empty lists, which the dashboard reads`() {
        link("1", "w1")
        reportMembers(mapOf("1" to listOf("r-veteran")))

        previewRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""").andExpect {
            jsonPath("$.gains") { isArray() }
            jsonPath("$.losses") { isArray() }
            jsonPath("$.unknown") { isArray() }
        }
        // Without the members of the bot, the lists are empty as well.
        memberService.forget()
        previewRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""").andExpect { jsonPath("$.gains") { isArray() } }
    }

    @Test
    fun `preview without the members of the bot says so`() {
        link("1", "w1")

        val preview = preview("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")

        assertEquals(false, preview.membersReported)
        assertEquals(emptyList(), preview.gains + preview.losses + preview.unknown)
    }

    @Test
    fun `only assignable roles can be picked`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")

        val roles = mockMvc.get("/web/admin/role-sync/roles") { cookie(admin) }
        assertEquals(listOf("Bürger Deutschland", "Minister", "Veteran"), roles.read("$[*].name"))
        assertEquals(listOf(false, false, true), roles.read("$[*].managed"))
        createRule("""{"roleId":"r-mod","type":"LEVEL","threshold":10}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `admin creates a rule of each type and deletes it`() {
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""").andExpect {
            status { isCreated() }
            jsonPath("$.label") { value("Staatsbürgerschaft Germany") }
            jsonPath("$.status") { value("ACTIVE") }
        }
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"MINISTER_OF_DEFENSE"}""")
            .andExpect { jsonPath("$.label") { value("Verteidigungsminister in Germany") } }
        createRule("""{"roleId":"r-veteran","type":"MILITARY_UNIT","muId":"m1"}""")
            .andExpect { jsonPath("$.label") { value("Mitglied der MU Deutsche Marine") } }
        val id =
            ruleId(createRule("""{"roleId":"r-veteran","type":"MILITARY_RANK","threshold":40}"""))

        assertEquals(
            listOf(
                "Staatsbürgerschaft Germany",
                "Verteidigungsminister in Germany",
                "Mitglied der MU Deutsche Marine",
                "Militärrang ab 40",
            ),
            mockMvc.get("/web/admin/role-sync/rules") { cookie(admin) }.read("$[*].label"),
        )
        mockMvc.delete("/web/admin/role-sync/rules/$id") { cookie(admin) }.andExpect { status { isNoContent() } }
        mockMvc.delete("/web/admin/role-sync/rules/$id") { cookie(admin) }.andExpect { status { isNotFound() } }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM role_rule", Int::class.java))
    }

    @Test
    fun `incomplete or unknown conditions are rejected`() {
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"xx"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-veteran","type":"MILITARY_UNIT","muId":"m9"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-veteran","type":"MILITARY_UNIT","muId":"not an id"}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":0}""").andExpect { status { isBadRequest() } }
        createRule("""{"roleId":"r-gone","type":"LEVEL","threshold":5}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `the same rule twice for a role is a conflict`() {
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""").andExpect { status { isCreated() } }

        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""").andExpect { status { isConflict() } }
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":20}""").andExpect { status { isCreated() } }
        createRule("""{"roleId":"r-citizen","type":"LEVEL","threshold":10}""").andExpect { status { isCreated() } }
    }

    @Test
    fun `the database rejects rules without the fields of their type`() {
        fun insert(
            type: String,
            countryId: String? = null,
            office: String? = null,
            threshold: Int? = null,
            targetName: String? = null,
        ) = jdbc.update(
            "INSERT INTO role_rule (role_id, type, country_id, office, threshold, target_name, created_at) VALUES ('r', ?, ?, ?, ?, ?, now())",
            type,
            countryId,
            office,
            threshold,
            targetName,
        )

        listOf(
            { insert("GOVERNMENT_OFFICE", countryId = "de", targetName = "Germany") },
            { insert("GOVERNMENT_OFFICE", countryId = "de", office = "KING", targetName = "Germany") },
            { insert("CITIZENSHIP", countryId = "de") },
            { insert("LEVEL") },
            { insert("LEVEL", threshold = 0) },
            { insert("LEVEL", threshold = 5, countryId = "de") },
            { insert("UNKNOWN", threshold = 5) },
        ).forEach { assertFailsWith<DataIntegrityViolationException> { it() } }
        assertEquals(1, insert("GOVERNMENT_OFFICE", countryId = "de", office = "CONGRESS", targetName = "Germany"))
    }

    @Test
    fun `rule change syncs all members`() {
        link("1", "w1")
        link("2", "w2")

        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")

        assertEquals(
            RoleSyncEvent(
                managedRoleIds = listOf("r-citizen"),
                discordUserIds = null,
                members = mapOf("1" to MemberRoles(listOf(RoleGrant("r-citizen", "BITS: Staatsbürgerschaft Germany")))),
                occurredAt = clock.millis(),
                confirmedRoleIds = listOf("r-citizen"),
                nicknames = emptyMap(),
            ),
            // The ID of the run in the system log, see SystemLogTest.
            (awaitEvents(1).single() as RoleSyncEvent).copy(syncId = null),
        )
    }

    @Test
    fun `rule change reuses the WarEra data of the last sync, the hourly sync loads it again`() {
        link("1", "w1")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitEvents(1)

        warera.users["w1"] = warera.users.getValue("w1").copy(leveling = WareraUser.Leveling(5))
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        assertEquals(setOf("r-citizen", "r-veteran"), grantedRoles(awaitEvents(2).last(), "1"))

        syncTrigger.hourly()
        val hourly = awaitEvents(3).last() as RoleSyncEvent
        assertEquals(setOf("r-citizen"), grantedRoles(hourly, "1"))
        // Nobody changed a rule, so the bot limits how many members lose a role.
        assertEquals(emptyList(), hourly.confirmedRoleIds)
    }

    @Test
    fun `linking syncs the member and takes the roles of the previous account`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        awaitEvents(1)

        verify("2", "Luftiger")

        val event = awaitEvents(2).last() as RoleSyncEvent
        assertEquals(listOf("2", "1"), event.discordUserIds)
        assertEquals(setOf("2"), event.members.keys)
    }

    @Test
    fun `unlinking syncs the member, who then gets none of the managed roles`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        assertEquals(setOf("r-citizen"), grantedRoles(awaitEvents(1).single(), "1"))

        mockMvc
            .post("/api/verifications/unlink") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"1"}"""
            }.andExpect { jsonPath("$.wareraUsername") { value("Luftiger") } }

        val event = awaitEvents(2).last() as RoleSyncEvent
        assertEquals(listOf("1"), event.discordUserIds)
        assertEquals(listOf("r-citizen"), event.managedRoleIds)
        assertEquals(emptySet(), event.members.keys)
    }

    @Test
    fun `opting in syncs the linked member with their WarEra name, even without rules`() {
        link("1", "w1")
        link("2", "w2")

        optIn("1", true).andExpect {
            jsonPath("$.enabled") { value(true) }
            jsonPath("$.wareraUsername") { value("Luftiger") }
        }

        val event = awaitEvents(1).single() as RoleSyncEvent
        assertEquals(listOf("1"), event.discordUserIds)
        assertEquals(emptyList(), event.managedRoleIds)
        assertEquals(mapOf("1" to "Luftiger"), event.nicknames)
        // Members who did not opt in keep their nickname.
        syncTrigger.hourly()
        assertEquals(mapOf("1" to "Luftiger"), (awaitEvents(2).last() as RoleSyncEvent).nicknames)
    }

    @Test
    fun `an opt-in before linking takes effect with the link and outlasts unlinking`() {
        optIn("2", true).andExpect { jsonPath("$.wareraUsername") { value(null) } }
        awaitEvents(0)

        verify("2", "Other")
        assertEquals(mapOf("2" to "Other"), (awaitEvents(1).last() as RoleSyncEvent).nicknames)

        unlink("2")
        val unlinked = awaitEvents(2).last() as RoleSyncEvent
        assertEquals(emptyMap(), unlinked.nicknames)
        assertEquals(mapOf("2" to "Other"), unlinked.nicknameResets)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM nickname_opt_in", Int::class.java))
    }

    @Test
    fun `nicknames follow a rename with the hourly sync, and opting out leaves them alone`() {
        link("1", "w1")
        optIn("1", true)
        awaitEvents(1)
        // Opting in again changes nothing.
        optIn("1", true)
        awaitEvents(1)

        warera.users["w1"] = warera.users.getValue("w1").copy(username = "Umbenannt")
        syncTrigger.hourly()
        assertEquals(mapOf("1" to "Umbenannt"), (awaitEvents(2).last() as RoleSyncEvent).nicknames)

        optIn("1", false).andExpect { jsonPath("$.enabled") { value(false) } }
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        assertEquals(emptyMap(), (awaitEvents(3).last() as RoleSyncEvent).nicknames)
    }

    @Test
    fun `members who opted in and lose their link to another account get the name to clear`() {
        link("1", "w1")
        optIn("1", true)
        awaitEvents(1)

        verify("2", "Luftiger")

        val relinked = awaitEvents(2).last() as RoleSyncEvent
        assertEquals(emptyMap(), relinked.nicknames)
        assertEquals(mapOf("1" to "Luftiger"), relinked.nicknameResets)
    }

    @Test
    fun `leaving the guild deletes the opt-in`() {
        link("1", "w1")
        optIn("1", true)
        awaitEvents(1)

        mockMvc
            .post("/api/discord-members/left") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"1","memberName":"Luca"}"""
            }.andExpect { status { isNoContent() } }

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM nickname_opt_in", Int::class.java))
    }

    @Test
    fun `rule changes while a sync runs are merged into one`() {
        // A user no sync has seen yet, so the first one has to look them up and can be held there.
        warera.users["w3"] = WareraUser("w3", "Third", country = "de", leveling = WareraUser.Leveling(1))
        link("3", "w3")
        warera.hold("w3")

        repeat(10) { createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":${it + 1}}""").andExpect { status { isCreated() } } }
        warera.release()

        assertEquals(2, awaitEvents(2).size)
    }

    @Test
    fun `a new link is synced while the hourly refresh runs, and the next refresh is skipped`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        awaitEvents(1)
        warera.lookups.clear()
        warera.hold("w1")
        syncTrigger.hourly()
        syncTrigger.hourly()

        verify("2", "Other")
        val memberSync = (1..100).firstNotNullOfOrNull { roleSyncEvents().getOrNull(1) ?: null.also { Thread.sleep(50) } }
        assertEquals(listOf("2"), (memberSync as RoleSyncEvent?)?.discordUserIds)

        warera.release()
        awaitEvents(3)
        // The second refresh was skipped, and the sync after the first one used the data it loaded.
        assertEquals(1, warera.lookups.count { it == "w1" })
    }

    @Test
    fun `office rules check the government of the country`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"PRESIDENT"}""")

        assertEquals(setOf("2"), (awaitEvents(1).single() as RoleSyncEvent).members.keys)
    }

    @Test
    fun `failed lookups keep the roles of the affected members`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"PRESIDENT"}""")
        awaitEvents(2)

        warera.failingUsers += "w1"
        warera.governmentDown = true
        syncTrigger.hourly()

        val event = awaitEvents(3).last() as RoleSyncEvent
        assertEquals(MemberRoles(grants = emptyList(), keepRoleIds = listOf("r-citizen", "r-minister")), event.members["1"])
        assertEquals(MemberRoles(grants = emptyList(), keepRoleIds = listOf("r-minister")), event.members["2"])
    }

    @Test
    fun `rules of a deleted role are orphaned and not run`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        awaitEvents(2)

        reportRoles("""{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""")

        val rules = mockMvc.get("/web/admin/role-sync/rules") { cookie(admin) }
        assertEquals(listOf("ACTIVE", "ORPHANED"), rules.read("$[*].status"))
        assertEquals(null, rules.read<String?>("$[1].roleName"))
        syncService.syncAll()
        assertEquals(listOf("r-veteran"), (awaitEvents(3).last() as RoleSyncEvent).managedRoleIds)
    }

    @Test
    fun `role explanation names the rule that is met and what is missing`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"PRESIDENT"}""")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        createRule("""{"roleId":"r-veteran","type":"MILITARY_RANK","threshold":50}""")
        awaitBackgroundWork()

        assertEquals(
            RoleExplanationResponse.Explained(
                wareraUsername = "Other",
                roles =
                    listOf(
                        ManagedRoleExplanation(
                            "r-citizen",
                            ManagedRoleOutcome.NOT_MET,
                            listOf(RuleExplanation("Staatsbürgerschaft Germany", false, "keine Staatsbürgerschaft Germany")),
                        ),
                        ManagedRoleExplanation(
                            "r-minister",
                            ManagedRoleOutcome.MET,
                            listOf(RuleExplanation("Präsident in Germany", true, null)),
                        ),
                        ManagedRoleExplanation(
                            "r-veteran",
                            ManagedRoleOutcome.NOT_MET,
                            listOf(
                                RuleExplanation("Level ab 10", false, "Level 3 von 10"),
                                RuleExplanation("Militärrang ab 50", false, "Militärrang 0 von 50"),
                            ),
                        ),
                    ),
            ),
            explain("2"),
        )
    }

    @Test
    fun `role explanation agrees with the sync, also when a government is missing`() {
        link("1", "w1")
        link("2", "w2")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-minister","type":"GOVERNMENT_OFFICE","countryId":"de","office":"PRESIDENT"}""")
        createRule("""{"roleId":"r-veteran","type":"MILITARY_RANK","threshold":40}""")
        // Rule changes right after one another may be merged into one sync, so only the syncs after them are counted.
        awaitBackgroundWork()

        listOf(false, true).forEach { governmentDown ->
            warera.governmentDown = governmentDown
            val explanations = listOf("1", "2").associateWith { (explain(it) as RoleExplanationResponse.Explained).roles }
            val before = roleSyncEvents().size
            syncService.syncMembers(listOf("1", "2"))
            val sync = awaitEvents(before + 1).last() as RoleSyncEvent

            explanations.forEach { (discordUserId, roles) ->
                val member = sync.members[discordUserId] ?: MemberRoles(emptyList())
                assertEquals(
                    member.grants.mapTo(HashSet()) { it.roleId },
                    roles.filter { it.outcome == ManagedRoleOutcome.MET }.mapTo(HashSet()) { it.roleId },
                )
                assertEquals(
                    member.keepRoleIds.toSet(),
                    roles.filter { it.outcome == ManagedRoleOutcome.UNDECIDED }.mapTo(HashSet()) { it.roleId },
                )
            }
        }
        // The missing government leaves the office role undecided for the member who is not the president.
        val minister = (explain("1") as RoleExplanationResponse.Explained).roles.single { it.roleId == "r-minister" }
        assertEquals(ManagedRoleOutcome.UNDECIDED, minister.outcome)
        assertEquals("Regierung von Germany gerade nicht abrufbar", minister.rules.single().detail)
    }

    @Test
    fun `role explanation leaves out orphaned rules, changes nothing and points unlinked members to verify`() {
        link("1", "w1")
        createRule("""{"roleId":"r-citizen","type":"CITIZENSHIP","countryId":"de"}""")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        reportRoles("""{"id":"r-veteran","name":"Veteran","color":null,"position":1,"assignable":true}""")
        awaitBackgroundWork()
        val before = roleSyncEvents().size

        assertEquals(listOf("r-veteran"), (explain("1") as RoleExplanationResponse.Explained).roles.map { it.roleId })
        assertEquals(RoleExplanationResponse.NotLinked, explain("9"))
        awaitEvents(before)
    }

    @Test
    fun `role explanation is a 503 if the user cannot be loaded`() {
        link("1", "w1")
        createRule("""{"roleId":"r-veteran","type":"LEVEL","threshold":10}""")
        warera.failingUsers += "w1"

        explainRequest("1").andExpect { status { isServiceUnavailable() } }
        explainRequest("not-an-id").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `countries and military units can be picked`() {
        assertEquals(listOf("France", "Germany"), mockMvc.get("/web/admin/role-sync/countries") { cookie(admin) }.read("$[*].name"))
        mockMvc
            .get("/web/admin/role-sync/military-units") {
                cookie(admin)
                param("query", "Marine")
            }.andExpect { jsonPath("$[0].name") { value("Deutsche Marine") } }

        warera.down = true
        mockMvc.get("/web/admin/role-sync/countries") { cookie(admin) }.andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `role report needs the service token`() {
        mockMvc
            .put("/api/discord-roles") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roles":[]}"""
            }.andExpect { status { isUnauthorized() } }
    }

    private fun reportRoles(vararg roles: String) {
        mockMvc
            .put("/api/discord-roles") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roles":[${roles.joinToString(",")}]}"""
            }.andExpect { status { isNoContent() } }
    }

    private fun explain(discordUserId: String): RoleExplanationResponse =
        BitsJson.decodeFromString(
            explainRequest(discordUserId)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun explainRequest(discordUserId: String) =
        mockMvc.post("/api/role-explanations") {
            header("Authorization", "Bearer test-service-token-at-least-32-chars")
            contentType = MediaType.APPLICATION_JSON
            content = """{"discordUserId":"$discordUserId"}"""
        }

    private fun previewRule(
        body: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/role-sync/rules/preview") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun preview(body: String): RulePreviewResponse =
        BitsJson.decodeFromString(
            previewRule(body)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun deletionPreview(id: Long): RulePreviewResponse =
        BitsJson.decodeFromString(
            mockMvc
                .get("/web/admin/role-sync/rules/$id/deletion-preview") { cookie(admin) }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    /** Every member of [holders], with the roles they hold. */
    private fun reportMembers(holders: Map<String, List<String>>) {
        val members = holders.map { (id, roleIds) -> ReportedMember(id, "Member $id", roleIds) }
        mockMvc
            .put("/api/discord-members") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = BitsJson.encodeToString(DiscordMembersReport(holders.keys.toList(), members))
            }.andExpect { status { isNoContent() } }
    }

    /** Who gets and who loses [roleId] when the bot applies [event] to [holders], as its `roleChanges` decides. */
    private fun changesOf(
        event: RoleSyncEvent,
        holders: Map<String, List<String>>,
        roleId: String,
    ): Pair<Set<String>, Set<String>> {
        val gains = HashSet<String>()
        val losses = HashSet<String>()
        holders.forEach { (id, roleIds) ->
            val target = event.members[id]
            val granted = target?.grants.orEmpty().any { it.roleId == roleId }
            val kept = roleId in target?.keepRoleIds.orEmpty()
            val holds = roleId in roleIds
            if (granted && !holds) gains += id
            if (!granted && !kept && holds && roleId in event.managedRoleIds) losses += id
        }
        return gains to losses
    }

    private fun createRule(
        body: String,
        session: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/role-sync/rules") {
            cookie(session)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun optIn(
        discordUserId: String,
        enabled: Boolean,
    ) = mockMvc.post("/api/nickname-preferences") {
        header("Authorization", "Bearer test-service-token-at-least-32-chars")
        contentType = MediaType.APPLICATION_JSON
        content = """{"discordUserId":"$discordUserId","enabled":$enabled}"""
    }

    private fun unlink(discordUserId: String) =
        mockMvc
            .post("/api/verifications/unlink") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"$discordUserId"}"""
            }.andExpect { status { isOk() } }

    private fun ruleId(result: ResultActionsDsl): Long = result.read<Number>("$.id").toLong()

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

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

    /** Links through the API, which is what starts the sync of the member. */
    private fun verify(
        discordUserId: String,
        username: String,
    ) {
        val started =
            mockMvc
                .post("/api/verifications") {
                    header("Authorization", "Bearer test-service-token-at-least-32-chars")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"discordUserId":"$discordUserId","wareraUsername":"$username"}"""
                }.andReturn()
                .response.contentAsString
        val code = JsonPath.read<String>(started, "$.code")
        warera.companyName = code
        mockMvc
            .post("/api/verifications/check") {
                header("Authorization", "Bearer test-service-token-at-least-32-chars")
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"$discordUserId","code":"$code"}"""
            }.andExpect { jsonPath("$.type") { value("checking") } }
        // The check runs in the background. Its result is published after the link, which starts the sync of the member.
        repeat(100) {
            val result = streamEvents().filterIsInstance<VerificationCheckedEvent>().singleOrNull { it.code == code }?.result
            if (result != null) return assertEquals(CheckVerificationResponse.Linked(username), result)
            Thread.sleep(50)
        }
        fail("no result for the check of $code")
    }

    private fun grantedRoles(
        event: BitsEvent,
        discordUserId: String,
    ) = (event as RoleSyncEvent)
        .members[discordUserId]
        ?.grants
        .orEmpty()
        .mapTo(HashSet()) { it.roleId }

    /** Syncs run in the background, one after the other; waits for them and expects exactly [count] role syncs. */
    private fun awaitEvents(count: Int): List<BitsEvent> {
        awaitBackgroundWork()
        return roleSyncEvents().also { assertEquals(count, it.size, "expected $count role syncs, got $it") }
    }

    /** A link starts a sync through the registry, and the sync publishes its result through it again. */
    private fun awaitBackgroundWork() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        jdbc.awaitOutbox()
    }

    private fun roleSyncEvents() = streamEvents().filterIsInstance<RoleSyncEvent>()

    private fun streamEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }

    /**
     * Luftiger (w1) is a German citizen at level 30 in the Marine; Other (w2) is French, but President of Germany.
     * Each has one company for the verification.
     */
    class FakeWarera : WareraClient {
        val users = mutableMapOf<String, WareraUser>()
        val failingUsers = mutableSetOf<String>()

        /** Users WarEra no longer knows. */
        val deletedUsers = mutableSetOf<String>()
        val lookups = CopyOnWriteArrayList<String>()
        private val heldUsers = ConcurrentHashMap.newKeySet<String>()
        private var gate = CountDownLatch(0)
        var governmentDown = false
        var down = false
        var companyName = ""

        /** Lookups of [wareraUserIds] wait until [release], so a sync can be caught while it runs. */
        fun hold(vararg wareraUserIds: String) {
            gate = CountDownLatch(1)
            heldUsers += wareraUserIds
        }

        fun release() {
            heldUsers.clear()
            gate.countDown()
        }

        fun reset() {
            release()
            lookups.clear()
            users.clear()
            users["w1"] = WareraUser("w1", "Luftiger", country = "de", mu = "m1", militaryRank = 40, leveling = WareraUser.Leveling(30))
            users["w2"] = WareraUser("w2", "Other", country = "fr", leveling = WareraUser.Leveling(3))
            failingUsers.clear()
            deletedUsers.clear()
            governmentDown = false
            down = false
            companyName = ""
        }

        override fun getAllCountries() =
            available {
                listOf(
                    WareraCountry(
                        "de",
                        "Germany",
                        "de",
                        currentPopulation = 982,
                        rankings = WareraCountry.Rankings(WareraCountry.Ranking(925)),
                    ),
                    WareraCountry("fr", "France", "fr"),
                )
            }

        override fun searchUserIds(text: String) = available { users.values.filter { it.username == text }.map { it.id } }

        override fun getUser(userId: String) =
            available {
                lookups += userId
                if (userId in heldUsers) gate.await(10, TimeUnit.SECONDS)
                // WarEra changed its answer in a way BITS cannot read.
                if (userId in failingUsers) throw WareraSchemaException("unexpected response")
                if (userId in deletedUsers) throw WareraNotFoundException("no such user")
                users.getValue(userId)
            }

        override fun getCompanyIds(userId: String) = available { listOf("c-$userId") }

        override fun getCompany(companyId: String) =
            available { WareraCompany(companyId, companyName, ownerId = companyId.removePrefix("c-")) }

        override fun getGovernment(countryId: String) =
            available {
                if (governmentDown) throw WareraApiException("down")
                WareraGovernment(country = countryId, president = "w2".takeIf { countryId == "de" })
            }

        override fun searchMilitaryUnitIds(text: String) = available { listOf("m1") }

        override fun getMilitaryUnit(muId: String) =
            available {
                if (" " in muId) throw WareraBadRequestException("invalid id")
                if (muId != "m1") throw WareraNotFoundException("no such MU")
                WareraMilitaryUnit("m1", "Deutsche Marine")
            }

        private fun <T> available(block: () -> T): T = if (down) throw WareraApiException("down") else block()
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fakes {
        @Bean
        @Primary
        fun fakeWareraClient() = FakeWarera()

        @Bean
        @Primary
        fun mutableClock() = MutableClock()
    }
}
