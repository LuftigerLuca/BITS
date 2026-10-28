package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.embassy.internal.EmbassySyncTrigger
import eu.luftiger.bits.backend.embassy.internal.EmbassyThreadCreationService
import eu.luftiger.bits.backend.members.internal.GuildMemberService
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.CreatedThread
import eu.luftiger.bits.shared.DiscordMembersReport
import eu.luftiger.bits.shared.EmbassySyncEvent
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadCreationEvent
import eu.luftiger.bits.shared.EmbassyThreadCreationReport
import eu.luftiger.bits.shared.EmbassyThreadStatus
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.ReportedEmbassyChange
import eu.luftiger.bits.shared.ReportedEmbassyThread
import eu.luftiger.bits.shared.ReportedMember
import eu.luftiger.bits.shared.ReportedThreadMember
import eu.luftiger.bits.shared.RoleChangeOutcome
import eu.luftiger.bits.shared.ThreadCreationFailure
import eu.luftiger.bits.shared.VerificationCheckedEvent
import eu.luftiger.bits.shared.decodeEvent
import eu.luftiger.bits.warera.WareraGovernment
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
import kotlin.test.fail

@SpringBootTest(properties = ["bits.service-token=test-service-token-at-least-32-chars"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class EmbassyTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    lateinit var sessionTokens: SessionTokens

    @Autowired
    lateinit var embassyTrigger: EmbassySyncTrigger

    @Autowired
    lateinit var roleSyncTrigger: RoleSyncTrigger

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    @Autowired
    lateinit var memberService: GuildMemberService

    @Autowired
    lateinit var creationService: EmbassyThreadCreationService

    private val admin by lazy { session(SessionUser.admin("admin")) }

    @BeforeTest
    fun reset() {
        awaitBackgroundWork()
        truncate()
        redisTemplate.delete(EventStream.KEY)
        memberService.forget()
        warera.reset()
        // France: Minister (w3) is Foreign Minister, Deputy (w4) sits in congress.
        warera.users["w3"] = WareraUser("w3", "Minister", country = "fr")
        warera.users["w4"] = WareraUser("w4", "Deputy", country = "fr")
        warera.users["w5"] = WareraUser("w5", "Guest", country = "fr")
        warera.governments["fr"] = WareraGovernment("fr", minOfForeignAffairs = "w3", congressMembers = listOf("w4"))
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        reportRoles(
            """{"id":"r-amb","name":"Botschafter","color":null,"position":2,"assignable":true}""",
            """{"id":"r-gov","name":"Regierung","color":null,"position":1,"assignable":true}""",
        )
        reportThreads(
            """{"id":"t-fr","name":"botschaft-frankreich","parentChannelId":"c-1","parentChannelName":"diplomatie","archived":false,"manageable":true}""",
            """{"id":"t-de","name":"botschaft-deutschland","parentChannelId":"c-1","parentChannelName":"diplomatie","archived":true,"manageable":true}""",
        )
    }

    /** Other tests share the database; with threads left, every link there would start an embassy sync. */
    @AfterTest
    fun cleanUp() {
        awaitBackgroundWork()
        truncate()
    }

    private fun truncate() {
        jdbc.execute(
            "TRUNCATE account_link, account_verification, embassy_thread, embassy_manual_member, embassy_thread_member, " +
                "discord_thread, embassy_host_role, embassy_setting, embassy_office, overview_setting, audit_entry, " +
                "system_log_entry, event_publication, discord_role, role_rule, nickname_opt_in, feature_setting, " +
                "embassy_thread_creation, discord_channel CASCADE",
        )
        jdbc.execute("INSERT INTO embassy_office (office) VALUES ('PRESIDENT'), ('VICE_PRESIDENT'), ('MINISTER_OF_FOREIGN_AFFAIRS')")
    }

    @Test
    fun `users get 403 on the embassies`() {
        val user = session(SessionUser.discord("42", "Luca", "https://avatar", Role.USER))

        mockMvc.get("/web/admin/embassies") { cookie(user) }.andExpect { status { isForbidden() } }
        assign("t-fr", "fr", user).andExpect { status { isForbidden() } }
        mockMvc
            .put("/web/admin/embassies/settings") {
                cookie(user)
                contentType = MediaType.APPLICATION_JSON
                content = """{"offices":[],"ambassadorRoleId":null,"hostRoleIds":[]}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `assigning a thread checks thread and country and is recorded`() {
        jdbc.update("INSERT INTO overview_setting (country_id, country_name, updated_at) VALUES ('de', 'Germany', now())")

        assign("t-unknown", "fr").andExpect { status { isBadRequest() } }
        assign("t-fr", "xx").andExpect { status { isBadRequest() } }
        // The own country gets no embassy.
        assign("t-de", "de").andExpect { status { isBadRequest() } }
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        assign("t-de", "fr").andExpect { status { isConflict() } }

        mockMvc.get("/web/admin/embassies") { cookie(admin) }.andExpect {
            status { isOk() }
            jsonPath("$.ownCountryId") { value("de") }
            jsonPath("$.threads[0].threadName") { value("botschaft-frankreich") }
            jsonPath("$.threads[0].countryName") { value("France") }
        }
        val embassies = embassies()
        assertEquals(listOf("t-de"), JsonPath.read<List<String>>(embassies, "$.availableThreads[*].id"))
        assertEquals(
            listOf("PRESIDENT", "VICE_PRESIDENT", "MINISTER_OF_FOREIGN_AFFAIRS"),
            JsonPath.read<List<String>>(embassies, "$.settings.offices"),
        )
        assertEquals(
            listOf("Botschafts-Thread botschaft-frankreich: France"),
            jdbc.queryForList("SELECT description FROM audit_entry", String::class.java),
        )
    }

    @Test
    fun `a foreign minister joins the thread of their country right after linking, a congress member does not`() {
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        setSettings(
            """{"offices":["PRESIDENT","VICE_PRESIDENT","MINISTER_OF_FOREIGN_AFFAIRS"],"ambassadorRoleId":"r-amb","hostRoleIds":["r-gov"]}""",
        )
        awaitBackgroundWork()

        verify("3", "Minister")
        verify("4", "Deputy")

        val event = lastSync()
        val thread = event.threads.single()
        assertEquals(mapOf("3" to "Außenminister von France"), thread.members)
        assertEquals("c-1", thread.parentChannelId)
        assertEquals(true, thread.complete)
        assertEquals(mapOf("3" to "BITS: Außenminister von France"), event.ambassadors)
        assertEquals("r-amb", event.ambassadorRoleId)
        assertEquals(listOf("r-gov"), event.hostRoleIds)
    }

    @Test
    fun `while the embassies are off nothing syncs, and switching them on syncs right away`() {
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        awaitBackgroundWork()
        val before = streamEvents().filterIsInstance<EmbassySyncEvent>().size

        setFeature("EMBASSIES", false)
        verify("3", "Minister")
        embassyTrigger.hourly()
        awaitBackgroundWork()
        assertEquals(before, streamEvents().filterIsInstance<EmbassySyncEvent>().size)

        setFeature("EMBASSIES", true)
        awaitBackgroundWork()
        assertEquals(before + 1, streamEvents().filterIsInstance<EmbassySyncEvent>().size)
        assertEquals(mapOf("3" to "Außenminister von France"), lastSync().threads.single().members)
    }

    @Test
    fun `losing the office drops a member unless an admin added them`() {
        link("3", "w3")
        link("5", "w5")
        reportMembers("3", "5")
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        addMember("t-fr", "5").andExpect { status { isCreated() } }
        addMember("t-fr", "5").andExpect { status { isConflict() } }
        awaitBackgroundWork()
        assertEquals(mapOf("3" to "Außenminister von France", "5" to "Manuell hinzugefügt"), lastSync().threads.single().members)

        warera.governments["fr"] = WareraGovernment("fr")
        embassyTrigger.hourly()
        awaitBackgroundWork()

        val event = lastSync()
        assertEquals(mapOf("5" to "Manuell hinzugefügt"), event.threads.single().members)
        assertEquals(mapOf("5" to "BITS: Manuell im Thread von France"), event.ambassadors)

        mockMvc.delete("/web/admin/embassies/threads/t-fr/members/5") { cookie(admin) }.andExpect { status { isNoContent() } }
        awaitBackgroundWork()
        assertEquals(emptyMap(), lastSync().threads.single().members)
        assertEquals(
            listOf(
                "Botschafts-Thread botschaft-frankreich: France",
                "Member 5 manuell zum Botschafts-Thread von France hinzugefügt",
                "Member 5 aus dem Botschafts-Thread von France entfernt",
            ),
            jdbc.queryForList("SELECT description FROM audit_entry ORDER BY id", String::class.java),
        )
    }

    @Test
    fun `an office that is not chosen does not count`() {
        link("4", "w4")
        setSettings("""{"offices":["PRESIDENT","CONGRESS"],"ambassadorRoleId":null,"hostRoleIds":[]}""")
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        awaitBackgroundWork()
        assertEquals(mapOf("4" to "Kongressmitglied von France"), lastSync().threads.single().members)

        setSettings("""{"offices":["PRESIDENT"],"ambassadorRoleId":null,"hostRoleIds":[]}""")
        awaitBackgroundWork()

        assertEquals(emptyMap(), lastSync().threads.single().members)
    }

    @Test
    fun `without threads, nothing syncs and nothing is logged`() {
        verify("3", "Minister")
        embassyTrigger.hourly()
        awaitBackgroundWork()

        assertEquals(emptyList(), streamEvents().filterIsInstance<EmbassySyncEvent>())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM system_log_entry WHERE kind = 'EMBASSY_SYNC_RUN'", Int::class.java))
    }

    @Test
    fun `a government that cannot be loaded keeps everybody in its thread and is logged once`() {
        link("3", "w3")
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        awaitBackgroundWork()
        warera.governmentDown = true

        embassyTrigger.hourly()
        awaitBackgroundWork()

        val thread = lastSync().threads.single()
        assertEquals(false, thread.complete)
        assertEquals(emptyMap(), thread.members)
        assertEquals(
            listOf(
                "Die Regierung von France konnte nicht geladen werden. Aus ihrem Botschafts-Thread wurde niemand wegen des Amts entfernt.",
            ),
            jdbc.queryForList("SELECT message FROM system_log_entry WHERE kind = 'GOVERNMENT_UNAVAILABLE'", String::class.java),
        )
    }

    @Test
    fun `the report of the bot shows who is in a thread and lands in the system log once`() {
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        assign("t-de", "de").andExpect { status { isCreated() } }
        awaitBackgroundWork()
        val syncId = lastSync().syncId
        val report =
            EmbassySyncReport(
                threads =
                    listOf(
                        ReportedEmbassyThread(
                            "t-fr",
                            "botschaft-frankreich",
                            EmbassyThreadStatus.SYNCED,
                            listOf(
                                ReportedThreadMember("3", "Minister", "Außenminister von France"),
                                ReportedThreadMember("9", "Ministerin", "Gastgeber-Rolle @Regierung"),
                            ),
                        ),
                        ReportedEmbassyThread("t-de", null, EmbassyThreadStatus.MISSING, emptyList()),
                    ),
                changes =
                    listOf(
                        ReportedEmbassyChange(
                            "3",
                            "Minister",
                            threadId = "t-fr",
                            threadName = "botschaft-frankreich",
                            outcome = RoleChangeOutcome.GRANTED,
                            reason = "Außenminister von France",
                        ),
                        ReportedEmbassyChange(
                            "3",
                            "Minister",
                            roleId = "r-amb",
                            roleName = "Botschafter",
                            outcome = RoleChangeOutcome.GRANTED,
                        ),
                    ),
            )

        repeat(2) { postReport(syncId, report).andExpect { status { isNoContent() } } }
        postReport(999_999, report).andExpect { status { isNotFound() } }

        val embassies = embassies()
        assertEquals("France", JsonPath.read<String>(embassies, "$.threads[0].countryName"))
        assertEquals(
            listOf("Außenminister von France", "Gastgeber-Rolle @Regierung"),
            JsonPath.read<List<String>>(embassies, "$.threads[0].members[*].reason"),
        )
        assertEquals(
            listOf("THREAD_ADDED:Thread botschaft-frankreich: Außenminister von France", "GRANTED:null"),
            jdbc.queryForList("SELECT outcome || ':' || COALESCE(reason, 'null') FROM system_log_item ORDER BY id", String::class.java),
        )
        assertEquals(
            listOf(
                "Der Botschafts-Thread botschaft-deutschland (Germany) wurde nicht abgeglichen: Er wurde gelöscht, oder der Bot sieht ihn nicht.",
            ),
            jdbc.queryForList("SELECT message FROM system_log_entry WHERE kind = 'EMBASSY_THREAD_UNAVAILABLE'", String::class.java),
        )
        mockMvc.get("/web/admin/system-log?category=SYNC") { cookie(admin) }.andExpect {
            jsonPath("$.entries[0].type") { value("EMBASSY_SYNC_RUN") }
            jsonPath("$.entries[0].sync.status") { value("APPLIED") }
            jsonPath("$.entries[0].sync.granted") { value(2) }
        }
    }

    @Test
    fun `the download of a member lists the threads they are in`() {
        reportMembers("5")
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        addMember("t-fr", "5").andExpect { status { isCreated() } }
        awaitBackgroundWork()
        val report =
            EmbassySyncReport(
                threads =
                    listOf(
                        ReportedEmbassyThread(
                            "t-fr",
                            "botschaft-frankreich",
                            EmbassyThreadStatus.SYNCED,
                            listOf(ReportedThreadMember("5", "Member 5", "Manuell hinzugefügt")),
                        ),
                    ),
                changes = emptyList(),
            )
        postReport(lastSync().syncId, report).andExpect { status { isNoContent() } }

        val member = session(SessionUser.discord("5", "Member 5", "https://avatar", Role.USER))
        val export =
            mockMvc
                .get("/web/account/export") { cookie(member) }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString

        assertEquals(listOf("France"), JsonPath.read<List<String>>(export, "$.embassies.addedByHand[*].countryName"))
        assertEquals(listOf("Manuell hinzugefügt"), JsonPath.read<List<String>>(export, "$.embassies.reportedIn[*].reason"))
    }

    @Test
    fun `settings take only known roles and are recorded`() {
        setSettingsRequest("""{"offices":[],"ambassadorRoleId":"r-unknown","hostRoleIds":[]}""").andExpect { status { isBadRequest() } }
        setSettings("""{"offices":["MINISTER_OF_FOREIGN_AFFAIRS","PRESIDENT"],"ambassadorRoleId":"r-amb","hostRoleIds":["r-gov"]}""")

        val embassies = embassies()
        assertEquals(listOf("PRESIDENT", "MINISTER_OF_FOREIGN_AFFAIRS"), JsonPath.read<List<String>>(embassies, "$.settings.offices"))
        assertEquals("r-amb", JsonPath.read<String>(embassies, "$.settings.ambassadorRoleId"))
        assertEquals(listOf("r-gov"), JsonPath.read<List<String>>(embassies, "$.settings.hostRoleIds"))
        assertEquals(
            listOf(
                "Botschaften: Ämter: Präsident, Außenminister; Botschafter-Rolle: @Botschafter; Gastgeber-Rollen: @Regierung; Botschafts-Kanal: keiner",
            ),
            jdbc.queryForList("SELECT description FROM audit_entry", String::class.java),
        )
    }

    @Test
    fun `roles and members who may not see the channel are refused, since Discord would not add them`() {
        reportThreads(
            """{"id":"t-fr","name":"botschaft-frankreich","parentChannelId":"c-1","parentChannelName":"diplomatie",""" +
                """"archived":false,"manageable":true,"everyoneCanView":false,"viewerRoleIds":["r-gov"]}""",
        )
        reportMembers(mapOf("3" to emptyList(), "5" to listOf("r-gov")))
        setSettings("""{"offices":[],"ambassadorRoleId":"r-amb","hostRoleIds":[]}""")

        assign("t-fr", "fr").andExpect { status { isUnprocessableContent() } }
        setSettings("""{"offices":[],"ambassadorRoleId":null,"hostRoleIds":["r-gov"]}""")
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        setSettingsRequest("""{"offices":[],"ambassadorRoleId":"r-amb","hostRoleIds":[]}""")
            .andExpect { status { isUnprocessableContent() } }
        addMember("t-fr", "3").andExpect { status { isUnprocessableContent() } }
        addMember("t-fr", "5").andExpect { status { isCreated() } }

        // Once the ambassador role may see the channel, it takes everybody in.
        reportThreads(
            """{"id":"t-fr","name":"botschaft-frankreich","parentChannelId":"c-1","parentChannelName":"diplomatie",""" +
                """"archived":false,"manageable":true,"everyoneCanView":false,"viewerRoleIds":["r-gov","r-amb"]}""",
        )
        setSettings("""{"offices":[],"ambassadorRoleId":"r-amb","hostRoleIds":[]}""")
        addMember("t-fr", "3").andExpect { status { isCreated() } }
    }

    @Test
    fun `the embassy channel must exist, be visible to the roles and let the bot create threads`() {
        reportChannels(
            channel("c-open", creatable = true),
            channel("c-closed", creatable = false),
            channel("c-hidden", creatable = true, viewers = listOf("r-gov")),
        )
        createThread("fr").andExpect { status { isBadRequest() } }

        setSettingsRequest(settings(channelId = "c-unknown")).andExpect { status { isBadRequest() } }
        setSettingsRequest(settings(channelId = "c-closed")).andExpect { status { isUnprocessableContent() } }
        setSettingsRequest(settings(channelId = "c-hidden")).andExpect { status { isUnprocessableContent() } }
        setSettings(settings(channelId = "c-open"))

        val embassies = embassies()
        assertEquals("c-open", JsonPath.read<String>(embassies, "$.settings.channelId"))
        assertEquals(listOf(true, false, true), JsonPath.read<List<Boolean>>(embassies, "$.channels[*].threadsCreatable"))
        assertEquals(
            "Botschaften: Ämter: Präsident, Vizepräsident, Außenminister; Botschafter-Rolle: @Botschafter; " +
                "Gastgeber-Rollen: keine; Botschafts-Kanal: #c-open",
            jdbc.queryForList("SELECT description FROM audit_entry", String::class.java).last(),
        )
    }

    @Test
    fun `an admin has the bot create a thread, which is assigned and synced right away, once`() {
        jdbc.update("INSERT INTO overview_setting (country_id, country_name, updated_at) VALUES ('de', 'Germany', now())")
        reportChannels(channel("c-1", creatable = true))
        setSettings(settings(channelId = "c-1"))
        createThread("de").andExpect { status { isBadRequest() } }
        createThread("fr", " ").andExpect { status { isBadRequest() } }
        createThread("fr", "x".repeat(101)).andExpect { status { isBadRequest() } }

        createThread("fr", " Botschaft France ").andExpect { status { isAccepted() } }
        // A second click, or assigning a thread meanwhile, gets no second thread.
        createThread("fr").andExpect { status { isConflict() } }
        assign("t-fr", "fr").andExpect { status { isConflict() } }

        val event = creationEvents().single()
        assertEquals("c-1", event.channelId)
        assertEquals("Botschaft France", event.name)
        assertEquals("France", event.countryName)
        val pending = embassies()
        assertEquals(listOf("France"), JsonPath.read<List<String>>(pending, "$.creations[*].countryName"))
        assertEquals(listOf(null), JsonPath.read<List<String?>>(pending, "$.creations[*].failure"))
        awaitBackgroundWork()
        val syncs = streamEvents().filterIsInstance<EmbassySyncEvent>().size

        val created = EmbassyThreadCreationReport(thread = CreatedThread("t-new", "Botschaft France", "c-1", "diplomatie"))
        postCreation(event.creationId, created).andExpect { status { isNoContent() } }
        // The same report again changes nothing.
        postCreation(event.creationId, created).andExpect { status { isNoContent() } }
        awaitBackgroundWork()

        val embassies = embassies()
        assertEquals(emptyList(), JsonPath.read<List<Any>>(embassies, "$.creations"))
        assertEquals(listOf("t-new"), JsonPath.read<List<String>>(embassies, "$.threads[*].threadId"))
        assertEquals(listOf("diplomatie"), JsonPath.read<List<String>>(embassies, "$.threads[*].channelName"))
        assertEquals(listOf(false), JsonPath.read<List<Boolean>>(embassies, "$.threads[*].missing"))
        assertEquals(syncs + 1, streamEvents().filterIsInstance<EmbassySyncEvent>().size)
        assertEquals(listOf("t-new"), lastSync().threads.map { it.threadId })
        createThread("fr").andExpect { status { isConflict() } }
        assertEquals(
            "Botschafts-Thread Botschaft France für France vom Bot anlegen lassen",
            jdbc.queryForList("SELECT description FROM audit_entry", String::class.java).last(),
        )
    }

    @Test
    fun `a foreign office holder who links gets their country a thread, a congress member does not`() {
        reportChannels(channel("c-1", creatable = true))
        setSettings(settings(channelId = "c-1"))

        verify("4", "Deputy")
        assertEquals(emptyList(), creationEvents())

        verify("3", "Minister")
        val event = creationEvents().single()
        assertEquals("Botschaft France", event.name)
        assertEquals(
            "Botschafts-Thread Botschaft France automatisch angefordert: Außenminister von France ist verknüpft",
            jdbc.queryForObject(
                "SELECT description FROM audit_entry WHERE action = 'EMBASSY_THREAD_CREATION_REQUESTED'",
                String::class.java,
            ),
        )

        // The bot creates it, and the minister is in it with the sync that follows.
        postCreation(event.creationId, EmbassyThreadCreationReport(thread = CreatedThread("t-new", "Botschaft France", "c-1", "d")))
            .andExpect { status { isNoContent() } }
        awaitBackgroundWork()
        assertEquals(mapOf("3" to "Außenminister von France"), lastSync().threads.single().members)
    }

    @Test
    fun `a failed request is not repeated on its own`() {
        reportChannels(channel("c-1", creatable = true))
        setSettings(settings(channelId = "c-1"))
        verify("3", "Minister")
        val event = creationEvents().single()
        postCreation(event.creationId, EmbassyThreadCreationReport(failure = ThreadCreationFailure.FAILED))
            .andExpect { status { isNoContent() } }

        embassyTrigger.hourly()
        awaitBackgroundWork()
        assertEquals(1, creationEvents().size)
    }

    @Test
    fun `a failure or no answer within a minute frees the country and is logged`() {
        reportChannels(channel("c-1", creatable = true))
        setSettings(settings(channelId = "c-1"))
        createThread("fr").andExpect { status { isAccepted() } }
        val first = creationEvents().single()

        postCreation(first.creationId, EmbassyThreadCreationReport(failure = ThreadCreationFailure.NO_PERMISSION))
            .andExpect { status { isNoContent() } }
        assertEquals(
            listOf(
                "Dem Bot fehlen im Botschafts-Kanal die Rechte „Private Threads erstellen“, „Threads verwalten“ oder " +
                    "„Nachrichten in Threads senden“.",
            ),
            JsonPath.read<List<String>>(embassies(), "$.creations[*].failure"),
        )

        // Trying again replaces the failed request; without an answer it fails after a minute.
        createThread("fr").andExpect { status { isAccepted() } }
        val second = creationEvents().last()
        clock.now = clock.now.plusSeconds(59)
        creationService.expire()
        assertEquals(listOf(null), JsonPath.read<List<String?>>(embassies(), "$.creations[*].failure"))
        clock.now = clock.now.plusSeconds(2)
        creationService.expire()
        assertEquals(
            listOf("Der Bot hat nicht innerhalb einer Minute geantwortet."),
            JsonPath.read<List<String>>(embassies(), "$.creations[*].failure"),
        )

        // A thread that comes too late is not taken; the bot deletes it.
        postCreation(second.creationId, EmbassyThreadCreationReport(thread = CreatedThread("t-late", "Botschaft France", "c-1", "d")))
            .andExpect { status { isGone() } }
        assertEquals(
            2,
            jdbc.queryForObject("SELECT COUNT(*) FROM system_log_entry WHERE kind = 'EMBASSY_THREAD_CREATION_FAILED'", Int::class.java),
        )

        // The country is free: assigning a thread clears the failure.
        assign("t-fr", "fr").andExpect { status { isCreated() } }
        assertEquals(emptyList(), JsonPath.read<List<Any>>(embassies(), "$.creations"))
    }

    @Test
    fun `a failed creation can be dismissed, a pending one cannot`() {
        reportChannels(channel("c-1", creatable = true))
        setSettings(settings(channelId = "c-1"))
        createThread("fr").andExpect { status { isAccepted() } }
        val id = creationEvents().single().creationId

        mockMvc.delete("/web/admin/embassies/creations/$id") { cookie(admin) }.andExpect { status { isConflict() } }
        postCreation(id, EmbassyThreadCreationReport(failure = ThreadCreationFailure.FAILED)).andExpect { status { isNoContent() } }
        mockMvc.delete("/web/admin/embassies/creations/$id") { cookie(admin) }.andExpect { status { isNoContent() } }
        assertEquals(emptyList(), JsonPath.read<List<Any>>(embassies(), "$.creations"))
    }

    @Test
    fun `members are found by name or ID`() {
        reportMembers("3", "5")

        assertEquals(
            listOf("5"),
            JsonPath.read<List<String>>(get("/web/admin/embassies/member-search?query=member 5"), "$[*].discordUserId"),
        )
        assertEquals(listOf("Member 3"), JsonPath.read<List<String>>(get("/web/admin/embassies/member-search?query=3"), "$[*].name"))
    }

    private fun embassies() = get("/web/admin/embassies")

    private fun settings(channelId: String?) =
        """{"offices":["PRESIDENT","VICE_PRESIDENT","MINISTER_OF_FOREIGN_AFFAIRS"],"ambassadorRoleId":"r-amb","hostRoleIds":[],""" +
            """"channelId":${channelId?.let { "\"$it\"" } ?: "null"}}"""

    private fun channel(
        id: String,
        creatable: Boolean,
        viewers: List<String>? = null,
    ) = """{"id":"$id","name":"$id","category":null,"position":0,"postable":true,"threadsCreatable":$creatable,""" +
        """"everyoneCanView":${viewers == null},"viewerRoleIds":[${viewers.orEmpty().joinToString(",") { "\"$it\"" }}]}"""

    private fun reportChannels(vararg channels: String) {
        mockMvc
            .put("/api/discord-channels") {
                header("Authorization", BOT_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"channels":[${channels.joinToString(",")}]}"""
            }.andExpect { status { isNoContent() } }
    }

    private fun createThread(
        countryId: String,
        threadName: String = "Botschaft",
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/embassies/creations") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"countryId":"$countryId","threadName":"$threadName"}"""
        }

    private fun postCreation(
        creationId: Long,
        report: EmbassyThreadCreationReport,
    ): ResultActionsDsl =
        mockMvc.post("/api/embassies/thread-creations/$creationId") {
            header("Authorization", BOT_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = BitsJson.encodeToString(EmbassyThreadCreationReport.serializer(), report)
        }

    private fun get(path: String): String =
        mockMvc
            .get(path) { cookie(admin) }
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

    private fun assign(
        threadId: String,
        countryId: String,
        cookie: Cookie = admin,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/embassies/threads") {
            cookie(cookie)
            contentType = MediaType.APPLICATION_JSON
            content = """{"threadId":"$threadId","countryId":"$countryId"}"""
        }

    private fun addMember(
        threadId: String,
        discordUserId: String,
    ): ResultActionsDsl =
        mockMvc.post("/web/admin/embassies/threads/$threadId/members") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"discordUserId":"$discordUserId"}"""
        }

    private fun setFeature(
        feature: String,
        enabled: Boolean,
    ) = mockMvc
        .put("/web/admin/features/$feature") {
            cookie(session(SessionUser.admin("admin")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"enabled":$enabled}"""
        }.andExpect { status { isNoContent() } }

    private fun setSettings(body: String) = setSettingsRequest(body).andExpect { status { isNoContent() } }

    private fun setSettingsRequest(body: String): ResultActionsDsl =
        mockMvc.put("/web/admin/embassies/settings") {
            cookie(admin)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun postReport(
        syncId: Long,
        report: EmbassySyncReport,
    ): ResultActionsDsl =
        mockMvc.post("/api/embassies/syncs/$syncId") {
            header("Authorization", BOT_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = BitsJson.encodeToString(EmbassySyncReport.serializer(), report)
        }

    private fun reportRoles(vararg roles: String) {
        mockMvc
            .put("/api/discord-roles") {
                header("Authorization", BOT_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"roles":[${roles.joinToString(",")}]}"""
            }.andExpect { status { isNoContent() } }
    }

    private fun reportThreads(vararg threads: String) {
        mockMvc
            .put("/api/discord-threads") {
                header("Authorization", BOT_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"threads":[${threads.joinToString(",")}]}"""
            }.andExpect { status { isNoContent() } }
    }

    private fun reportMembers(vararg discordUserIds: String) = reportMembers(discordUserIds.associateWith { emptyList() })

    private fun reportMembers(roleIdsByMember: Map<String, List<String>>) {
        val discordUserIds = roleIdsByMember.keys.toList()
        val members = roleIdsByMember.map { (id, roleIds) -> ReportedMember(id, "Member $id", roleIds) }
        mockMvc
            .put("/api/discord-members") {
                header("Authorization", BOT_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = BitsJson.encodeToString(DiscordMembersReport.serializer(), DiscordMembersReport(discordUserIds, members))
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

    /** Links through the API, which publishes `AccountLinked` and so starts the embassy sync. */
    private fun verify(
        discordUserId: String,
        username: String,
    ) {
        val started =
            mockMvc
                .post("/api/verifications") {
                    header("Authorization", BOT_TOKEN)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"discordUserId":"$discordUserId","wareraUsername":"$username"}"""
                }.andReturn()
                .response.contentAsString
        val code = JsonPath.read<String>(started, "$.code")
        warera.companyName = code
        mockMvc
            .post("/api/verifications/check") {
                header("Authorization", BOT_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"discordUserId":"$discordUserId","code":"$code"}"""
            }.andExpect { jsonPath("$.type") { value("checking") } }
        repeat(100) {
            val result = streamEvents().filterIsInstance<VerificationCheckedEvent>().singleOrNull { it.code == code }?.result
            if (result != null) {
                assertEquals(CheckVerificationResponse.Linked(username), result)
                return awaitBackgroundWork()
            }
            Thread.sleep(50)
        }
        fail("no result for the check of $code")
    }

    /** A link starts a sync through the registry, and the sync publishes its result through it again. */
    private fun awaitBackgroundWork() {
        jdbc.awaitOutbox()
        roleSyncTrigger.awaitIdle()
        embassyTrigger.awaitIdle()
        jdbc.awaitOutbox()
    }

    /** Written to Redis by the outbox after the commit. */
    private fun creationEvents(): List<EmbassyThreadCreationEvent> {
        jdbc.awaitOutbox()
        return streamEvents().filterIsInstance<EmbassyThreadCreationEvent>()
    }

    private fun lastSync(): EmbassySyncEvent = streamEvents().filterIsInstance<EmbassySyncEvent>().lastOrNull() ?: fail("no embassy sync")

    private fun streamEvents() =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }

    private companion object {
        const val BOT_TOKEN = "Bearer test-service-token-at-least-32-chars"
    }
}
