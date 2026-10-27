package eu.luftiger.bits.backend

import com.jayway.jsonpath.JsonPath
import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncTrigger
import eu.luftiger.bits.backend.vote.internal.VoteEvaluator
import eu.luftiger.bits.backend.vote.internal.VoteJobs
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.CastVoteResponse
import eu.luftiger.bits.shared.CloseVoteResponse
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.ParticipationResponse
import eu.luftiger.bits.shared.PartyResultDto
import eu.luftiger.bits.shared.StartVoteResponse
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteClosedEvent
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import eu.luftiger.bits.shared.VoteResultDto
import eu.luftiger.bits.shared.VoteStartedEvent
import eu.luftiger.bits.shared.VoteState
import eu.luftiger.bits.shared.VoteThreshold
import eu.luftiger.bits.shared.decodeEvent
import eu.luftiger.bits.warera.WareraParty
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
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = ["bits.service-token=$TOKEN"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, RoleSyncTest.Fakes::class)
class VoteTest {
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
    lateinit var evaluator: VoteEvaluator

    @Autowired
    lateinit var jobs: VoteJobs

    @Autowired
    lateinit var warera: RoleSyncTest.FakeWarera

    @Autowired
    lateinit var clock: MutableClock

    @BeforeTest
    fun reset() {
        jdbc.awaitOutbox()
        syncTrigger.awaitIdle()
        evaluator.awaitIdle()
        jdbc.awaitOutbox()
        jdbc.execute(
            "TRUNCATE vote, vote_manager_role, discord_channel, discord_role, account_link, audit_entry, system_log_entry, " +
                "event_publication, feature_setting CASCADE",
        )
        redisTemplate.delete(EventStream.KEY)
        warera.reset()
        clock.now = Instant.parse("2026-01-01T12:00:00Z")
        api("/api/discord-roles", put = true) {
            """{"roles":[
                {"id":"r-congress","name":"Kongress","color":null,"position":2,"assignable":true},
                {"id":"r-speaker","name":"Sprecher","color":null,"position":1,"assignable":true},
                {"id":"100","name":"@everyone","color":null,"position":-1,"assignable":false}
            ]}"""
        }.andExpect { status { isNoContent() } }
        api("/api/discord-channels", put = true) {
            """{"channels":[
                {"id":"c-votes","name":"abstimmungen","category":"Kongress","position":1,"postable":true},
                {"id":"c-news","name":"news","category":null,"position":0,"postable":false}
            ]}"""
        }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `only admins and members of the manager roles may start a vote from Discord`() {
        assertEquals(StartVoteResponse.Forbidden, start(actor("7", roles = listOf("r-speaker"))))

        setManagerRoles("r-speaker")
        val started = start(actor("7", roles = listOf("r-speaker"))) as StartVoteResponse.Started
        val byAdmin = start(actor("8", administrator = true)) as StartVoteResponse.Started

        jdbc.awaitOutbox()
        assertEquals(listOf(started.voteId, byAdmin.voteId), streamEvents().filterIsInstance<VoteStartedEvent>().map { it.voteId })
        assertEquals(
            listOf("VOTE_MANAGER_ROLES_CHANGED", "VOTE_STARTED", "VOTE_STARTED"),
            jdbc.queryForList("SELECT action FROM audit_entry ORDER BY id", String::class.java),
        )
        val vote = vote(started.voteId)
        assertEquals(VoteState.OPEN, vote.state)
        assertEquals(Instant.parse("2026-01-03T12:00:00Z").toEpochMilli(), vote.endsAt)
    }

    @Test
    fun `a vote needs a channel the bot may post in and an end in the future`() {
        assertEquals(StartVoteResponse.ChannelNotPostable, start(actor("8", administrator = true), channelId = "c-news"))
        startRequest(actor("8", administrator = true), endsAt = clock.now.minusSeconds(1)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `members vote once, can change their vote and need a link`() {
        link("1", "w1")
        val voteId = startByAdmin()

        assertEquals(CastVoteResponse.NotLinked, cast(voteId, "2", VoteChoice.YES))
        assertEquals(CastVoteResponse.Cast(previous = null), cast(voteId, "1", VoteChoice.YES))
        assertEquals(CastVoteResponse.Cast(previous = VoteChoice.YES), cast(voteId, "1", VoteChoice.NO))

        assertEquals(ParticipationResponse(voted = 1, eligible = 1), participation(voteId, "1", "2"))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vote_ballot", Int::class.java))
    }

    @Test
    fun `no vote is taken once the end has come, even before the job ran`() {
        link("1", "w1")
        val voteId = startByAdmin()

        clock.advance(Duration.ofHours(48))

        assertEquals(CastVoteResponse.Closed, cast(voteId, "1", VoteChoice.YES))
    }

    @Test
    fun `a due vote ends on its own and is evaluated by party once the bot reports the role members`() {
        // w1, w3 and w4 are German; w1 and w3 are in the same party, w4 in none. w2 is French and in a French party.
        warera.users["w3"] = warera.users.getValue("w1").copy(id = "w3", username = "Third")
        warera.users["w4"] = warera.users.getValue("w1").copy(id = "w4", username = "Fourth")
        warera.parties["de"] = listOf(WareraParty("p-de", "Deutsche Partei", listOf("w1", "w3", "w9")))
        warera.parties["fr"] = listOf(WareraParty("p-fr", "Parti", listOf("w2")))
        listOf("1" to "w1", "2" to "w2", "3" to "w3", "4" to "w4", "5" to "w5").forEach { (discord, warera) -> link(discord, warera) }
        val voteId = startByAdmin()
        cast(voteId, "1", VoteChoice.YES)
        cast(voteId, "2", VoteChoice.NO)
        // Member 5 votes, but loses the role before the end; their vote must not count.
        cast(voteId, "5", VoteChoice.NO)

        clock.advance(Duration.ofHours(48))
        jobs.run()
        jdbc.awaitOutbox()
        assertEquals(VoteState.COUNTING, vote(voteId).state)
        assertEquals(listOf(voteId), streamEvents().filterIsInstance<VoteClosedEvent>().map { it.voteId })

        reportElectorate(voteId, "1" to "Anna", "2" to "Bernd", "3" to "Clara", "4" to "Dora", "6" to "Unlinked")
        evaluator.awaitIdle()
        jdbc.awaitOutbox()

        assertEquals(VoteState.EVALUATED, vote(voteId).state)
        assertEquals(listOf(voteId), streamEvents().filterIsInstance<VoteEvaluatedEvent>().map { it.voteId })
        val result = result(voteId)
        assertEquals("Neue Steuern?", result.question)
        assertEquals(
            listOf(
                PartyResultDto("Deutsche Partei", yes = 1, no = 0, abstain = 0, eligible = 2),
                PartyResultDto("Parti", yes = 0, no = 1, abstain = 0, eligible = 1),
                PartyResultDto(null, yes = 0, no = 0, abstain = 0, eligible = 1),
            ),
            result.parties,
        )
        assertEquals(listOf("Clara", "Dora"), result.nonVoters)
        assertTrue(Base64.getDecoder().decode(result.image).take(4) == PNG_SIGNATURE)
        assertEquals(false, result.posted)
        // Nothing is left that ties a member to a vote.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vote_ballot", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vote_elector", Int::class.java))
        // The party IDs, for the colours an admin picks.
        assertEquals(
            listOf("p-de", "p-fr", null),
            jdbc.queryForList("SELECT party_id FROM vote_party_result ORDER BY position", String::class.java),
        )

        api("/api/votes/$voteId/result/posted").andExpect { status { isNoContent() } }
        assertEquals(true, result(voteId).posted)
    }

    @Test
    fun `a second report of the role members changes nothing`() {
        link("1", "w1")
        val voteId = startByAdmin()
        endByAdmin(voteId)

        reportElectorate(voteId, "1" to "Anna")
        evaluator.awaitIdle()
        reportElectorate(voteId, "1" to "Anna", "2" to "Bernd")
        evaluator.awaitIdle()

        assertEquals(listOf("Anna"), result(voteId).nonVoters)
    }

    @Test
    fun `an evaluation WarEra cannot serve is recorded once and tried again later`() {
        link("1", "w1")
        val voteId = startByAdmin()
        endByAdmin(voteId)
        warera.down = true

        reportElectorate(voteId, "1" to "Anna")
        evaluator.awaitIdle()
        clock.advance(Duration.ofMinutes(5))
        jobs.run()
        evaluator.awaitIdle()

        assertEquals(VoteState.COUNTING, vote(voteId).state)
        assertEquals(
            listOf("VOTE_EVALUATION_DELAYED"),
            jdbc.queryForList("SELECT kind FROM system_log_entry", String::class.java),
        )

        warera.down = false
        clock.advance(Duration.ofMinutes(5))
        jobs.run()
        evaluator.awaitIdle()

        assertEquals(VoteState.EVALUATED, vote(voteId).state)
    }

    @Test
    fun `the bot is asked again for the role members and to post the result if it does not answer`() {
        link("1", "w1")
        val voteId = startByAdmin()
        endByAdmin(voteId)

        clock.advance(Duration.ofMinutes(29))
        jobs.run()
        clock.advance(Duration.ofMinutes(1))
        jobs.run()
        jdbc.awaitOutbox()
        assertEquals(2, streamEvents().filterIsInstance<VoteClosedEvent>().size)

        reportElectorate(voteId, "1" to "Anna")
        evaluator.awaitIdle()
        clock.advance(Duration.ofMinutes(30))
        jobs.run()
        jdbc.awaitOutbox()
        assertEquals(2, streamEvents().filterIsInstance<VoteEvaluatedEvent>().size)

        api("/api/votes/$voteId/result/posted").andExpect { status { isNoContent() } }
        clock.advance(Duration.ofMinutes(30))
        jobs.run()
        jdbc.awaitOutbox()
        assertEquals(2, streamEvents().filterIsInstance<VoteEvaluatedEvent>().size)
    }

    @Test
    fun `a cancelled vote has no result and keeps no ballots`() {
        link("1", "w1")
        val voteId = startByAdmin()
        cast(voteId, "1", VoteChoice.YES)

        assertEquals(CloseVoteResponse.Forbidden, close(voteId, actor("7"), cancel = true))
        assertEquals(CloseVoteResponse.Closed, close(voteId, actor("8", administrator = true), cancel = true))
        assertEquals(CloseVoteResponse.NotOpen, close(voteId, actor("8", administrator = true), cancel = false))

        assertEquals(VoteState.CANCELLED, vote(voteId).state)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vote_ballot", Int::class.java))
        api("/api/votes/$voteId/result", get = true).andExpect { status { isNotFound() } }
        assertEquals(CastVoteResponse.Closed, cast(voteId, "1", VoteChoice.NO))
    }

    @Test
    fun `the dashboard shows votes only to managers`() {
        val user = session(SessionUser.discord("7", "Member", "https://avatar", Role.USER, discordRoleIds = listOf("r-speaker")))

        mockMvc.get("/web/votes/access") { cookie(user) }.andExpect { jsonPath("$.canManage") { value(false) } }
        mockMvc.get("/web/votes") { cookie(user) }.andExpect { status { isForbidden() } }
        mockMvc
            .put("/web/admin/votes/manager-roles") {
                cookie(user)
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleIds":["r-speaker"]}"""
            }.andExpect { status { isForbidden() } }

        setManagerRoles("r-speaker")

        mockMvc.get("/web/votes/access") { cookie(user) }.andExpect {
            jsonPath("$.canManage") { value(true) }
            jsonPath("$.canConfigure") { doesNotExist() }
        }
        val options = mockMvc.get("/web/votes/options") { cookie(user) }
        assertEquals(listOf("r-congress", "r-speaker"), options.read<List<String>>("$.roles[*].id"))
        assertEquals(listOf("c-votes"), options.read<List<String>>("$.channels[*].id"))
    }

    @Test
    fun `managers start, end and look up votes in the dashboard`() {
        link("1", "w1")
        link("2", "w2")
        val admin = session(SessionUser.discord("8", "Admin", "https://avatar", Role.ADMIN))
        val id =
            mockMvc
                .post("/web/votes") {
                    cookie(admin)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"question":"Neue Steuern?","roleId":"r-congress","channelId":"c-votes","endsAt":null,""" +
                        """"threshold":{"percent":75,"choice":"NO"}}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("OPEN") }
                    jsonPath("$.endsAt") { value(null) }
                    jsonPath("$.threshold.percent") { value(75) }
                    jsonPath("$.threshold.choice") { value("NO") }
                }.read<Number>("$.id")
                .toLong()
        cast(id, "1", VoteChoice.ABSTAIN)
        // An abstention is no No.
        participation(id, "1", "2")

        mockMvc.get("/web/votes") { cookie(admin) }.andExpect {
            jsonPath("$.open[0].threshold.percent") { value(75) }
            jsonPath("$.open[0].endedByThreshold") { value(false) }
            jsonPath("$.open[0].voted") { value(1) }
            jsonPath("$.open[0].eligible") { value(2) }
            jsonPath("$.open[0].roleName") { value("Kongress") }
        }
        mockMvc.post("/web/votes/$id/end") { cookie(admin) }.andExpect { status { isNoContent() } }
        mockMvc.post("/web/votes/$id/cancel") { cookie(admin) }.andExpect { status { isConflict() } }
        reportElectorate(id, "1" to "Anna")
        evaluator.awaitIdle()

        mockMvc.get("/web/votes") { cookie(admin) }.andExpect {
            jsonPath("$.open.length()") { value(0) }
            jsonPath("$.closed[0].status") { value("EVALUATED") }
            jsonPath("$.closed[0].closedBy") { value("Admin") }
            jsonPath("$.closed[0].resultText") {
                value(
                    "Ergebnis: Neue Steuern?\nGesamt: 0 Ja · 0 Nein · 1 Enthaltung · 1 von 1 haben abgestimmt (100 %)\n\n" +
                        "Parteilos: 0 Ja · 0 Nein · 1 Enthaltung · 1 von 1 haben abgestimmt (100 %)\n\nAlle haben abgestimmt.",
                )
            }
        }
    }

    @Test
    fun `while votes are off the dashboard starts none, and open votes can still be ended`() {
        val id = startByAdmin()
        val admin = session(SessionUser.admin("admin"))
        mockMvc
            .put("/web/admin/features/VOTES") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"enabled":false}"""
            }.andExpect { status { isNoContent() } }

        mockMvc
            .post("/web/votes") {
                cookie(admin)
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"Neue Steuern?","roleId":"r-congress","channelId":"c-votes","endsAt":null}"""
            }.andExpect { status { isLocked() } }
        mockMvc.post("/web/votes/$id/end") { cookie(admin) }.andExpect { status { isNoContent() } }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vote", Int::class.java))
        jdbc.execute("TRUNCATE feature_setting")
    }

    @Test
    fun `the result image is served as PNG`() {
        link("1", "w1")
        val voteId = startByAdmin()
        endByAdmin(voteId)
        reportElectorate(voteId, "1" to "Anna")
        evaluator.awaitIdle()

        val admin = session(SessionUser.admin("admin"))
        val body =
            mockMvc
                .get("/web/votes/$voteId/result.png") { cookie(admin) }
                .andExpect { content { contentType(MediaType.IMAGE_PNG) } }
                .andReturn()
                .response.contentAsByteArray
        assertEquals(PNG_SIGNATURE, body.take(4))
    }

    @Test
    fun `a vote ends on its own once more than the percentage of the eligible members chose the option`() {
        (1..10).forEach { link("$it", "w$it") }
        val voteId = startByAdmin(threshold(50))
        val members = (1..10).map { "$it" }.toTypedArray()

        (1..5).forEach { cast(voteId, "$it", VoteChoice.YES) }
        cast(voteId, "6", VoteChoice.NO)
        cast(voteId, "7", VoteChoice.ABSTAIN)
        // 5 of 10 are not more than 50 %.
        assertEquals(ParticipationResponse(voted = 7, eligible = 10), participation(voteId, *members))
        assertEquals(VoteState.OPEN, vote(voteId).state)

        cast(voteId, "8", VoteChoice.YES)
        participation(voteId, *members)

        jdbc.awaitOutbox()
        val vote = vote(voteId)
        assertEquals(VoteState.COUNTING, vote.state)
        assertTrue(vote.endedByThreshold)
        assertEquals(VoteThreshold(50, VoteChoice.YES), vote.threshold)
        assertEquals(listOf(voteId), streamEvents().filterIsInstance<VoteClosedEvent>().map { it.voteId })
        assertEquals(null, jdbc.queryForObject("SELECT closed_by_name FROM vote", String::class.java))
        assertEquals(CastVoteResponse.Closed, cast(voteId, "9", VoteChoice.NO))
        // Only the start is audited, like an end at the end date.
        assertEquals(
            listOf(
                "„Neue Steuern?“ (@Kongress, #abstimmungen), bis 03.01.2026, 13:00 Uhr, " +
                    "endet automatisch, sobald mehr als 50 % mit Ja stimmen",
            ),
            jdbc.queryForList("SELECT description FROM audit_entry", String::class.java),
        )
    }

    @Test
    fun `only the chosen option ends a vote, also without end date`() {
        (1..4).forEach { link("$it", "w$it") }
        val voteId = startByAdmin(threshold(50, "NO"), endsAt = null)

        (1..3).forEach { cast(voteId, "$it", VoteChoice.YES) }
        cast(voteId, "4", VoteChoice.ABSTAIN)
        participation(voteId, "1", "2", "3", "4")
        assertEquals(VoteState.OPEN, vote(voteId).state)

        (1..3).forEach { cast(voteId, "$it", VoteChoice.NO) }
        participation(voteId, "1", "2", "3", "4")

        assertEquals(VoteState.COUNTING, vote(voteId).state)
        assertTrue(vote(voteId).endedByThreshold)
    }

    @Test
    fun `ballots of members who lost the role do not count towards the threshold`() {
        (1..6).forEach { link("$it", "w$it") }
        val voteId = startByAdmin(threshold(50))
        (1..3).forEach { cast(voteId, "$it", VoteChoice.YES) }

        // 2 and 3 lost the role; 1 of 4 chose Yes.
        assertEquals(ParticipationResponse(voted = 1, eligible = 4), participation(voteId, "1", "4", "5", "6"))

        assertEquals(VoteState.OPEN, vote(voteId).state)
    }

    @Test
    fun `a vote without eligible members does not end by the threshold`() {
        val voteId = startByAdmin(threshold(1))

        assertEquals(ParticipationResponse(voted = 0, eligible = 0), participation(voteId, "1", "2"))

        assertEquals(VoteState.OPEN, vote(voteId).state)
    }

    @Test
    fun `the end date still ends a vote that stays below the threshold`() {
        (1..2).forEach { link("$it", "w$it") }
        val voteId = startByAdmin(threshold(99))
        cast(voteId, "1", VoteChoice.YES)
        participation(voteId, "1", "2")

        clock.advance(Duration.ofHours(48))
        jobs.run()

        val vote = vote(voteId)
        assertEquals(VoteState.COUNTING, vote.state)
        assertEquals(false, vote.endedByThreshold)
    }

    @Test
    fun `a vote ended by hand is not closed again by the threshold`() {
        (1..2).forEach { link("$it", "w$it") }
        val voteId = startByAdmin(threshold(40))
        cast(voteId, "1", VoteChoice.YES)
        endByAdmin(voteId)

        participation(voteId, "1", "2")

        jdbc.awaitOutbox()
        assertEquals(listOf(voteId), streamEvents().filterIsInstance<VoteClosedEvent>().map { it.voteId })
        assertEquals(false, vote(voteId).endedByThreshold)
    }

    @Test
    fun `the threshold must be 1 to 99 percent for Yes or No`() {
        val admin = actor("8", administrator = true)
        startRequest(admin, threshold = threshold(0)).andExpect { status { isBadRequest() } }
        startRequest(admin, threshold = threshold(100)).andExpect { status { isBadRequest() } }
        startRequest(admin, threshold = threshold(50, "ABSTAIN")).andExpect { status { isBadRequest() } }
        listOf(threshold(100), threshold(50, "ABSTAIN"), """{"percent":50}""").forEach { threshold ->
            mockMvc
                .post("/web/votes") {
                    cookie(session(SessionUser.admin("admin")))
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"question":"Neue Steuern?","roleId":"r-congress","channelId":"c-votes","endsAt":null,""" +
                        """"threshold":$threshold}"""
                }.andExpect { status { isBadRequest() } }
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vote", Int::class.java))
    }

    @Test
    fun `the bot's API needs the service token`() {
        mockMvc.get("/api/votes/open").andExpect { status { isUnauthorized() } }
    }

    private fun startByAdmin(): Long = (start(actor("8", administrator = true)) as StartVoteResponse.Started).voteId

    private fun endByAdmin(voteId: Long) =
        assertEquals(CloseVoteResponse.Closed, close(voteId, actor("8", administrator = true), cancel = false))

    private fun actor(
        discordUserId: String,
        roles: List<String> = emptyList(),
        administrator: Boolean = false,
    ) = """{"discordUserId":"$discordUserId","name":"Member $discordUserId","avatarUrl":null,""" +
        """"roleIds":[${roles.joinToString(",") { "\"$it\"" }}],"administrator":$administrator}"""

    private fun start(
        actor: String,
        channelId: String = "c-votes",
        endsAt: Instant? = clock.now.plus(Duration.ofHours(48)),
        threshold: String? = null,
    ): StartVoteResponse = decode(startRequest(actor, channelId, endsAt, threshold))

    private fun startRequest(
        actor: String,
        channelId: String = "c-votes",
        endsAt: Instant? = clock.now.plus(Duration.ofHours(48)),
        threshold: String? = null,
    ) = api("/api/votes") {
        """{"actor":$actor,"question":"Neue Steuern?","roleId":"r-congress","channelId":"$channelId",""" +
            """"endsAt":${endsAt?.toEpochMilli()},"threshold":$threshold}"""
    }

    private fun startByAdmin(
        threshold: String,
        endsAt: Instant? = clock.now.plus(Duration.ofHours(48)),
    ): Long = (start(actor("8", administrator = true), endsAt = endsAt, threshold = threshold) as StartVoteResponse.Started).voteId

    private fun threshold(
        percent: Int,
        choice: String = "YES",
    ) = """{"percent":$percent,"choice":"$choice"}"""

    private fun close(
        voteId: Long,
        actor: String,
        cancel: Boolean,
    ): CloseVoteResponse = decode(api("/api/votes/$voteId/close") { """{"actor":$actor,"cancel":$cancel}""" })

    private fun cast(
        voteId: Long,
        discordUserId: String,
        choice: VoteChoice,
    ): CastVoteResponse = decode(api("/api/votes/$voteId/ballots") { """{"discordUserId":"$discordUserId","choice":"$choice"}""" })

    private fun participation(
        voteId: Long,
        vararg roleMemberIds: String,
    ): ParticipationResponse =
        decode(api("/api/votes/$voteId/participation") { """{"roleMemberIds":[${roleMemberIds.joinToString(",") { "\"$it\"" }}]}""" })

    private fun reportElectorate(
        voteId: Long,
        vararg members: Pair<String, String>,
    ) {
        api("/api/votes/$voteId/electorate", put = true) {
            """{"members":[${members.joinToString(",") { (id, name) -> """{"discordUserId":"$id","name":"$name"}""" }}]}"""
        }.andExpect { status { isNoContent() } }
    }

    private fun vote(voteId: Long): VoteDto = decode(api("/api/votes/$voteId", get = true))

    private fun result(voteId: Long): VoteResultDto = decode(api("/api/votes/$voteId/result", get = true))

    private fun setManagerRoles(vararg roleIds: String) {
        mockMvc
            .put("/web/admin/votes/manager-roles") {
                cookie(session(SessionUser.admin("admin")))
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleIds":[${roleIds.joinToString(",") { "\"$it\"" }}]}"""
            }.andExpect { status { isNoContent() } }
    }

    private fun api(
        path: String,
        get: Boolean = false,
        put: Boolean = false,
        body: () -> String = { "{}" },
    ): ResultActionsDsl =
        when {
            get -> mockMvc.get(path) { header("Authorization", "Bearer $TOKEN") }
            put ->
                mockMvc.put(path) {
                    header("Authorization", "Bearer $TOKEN")
                    contentType = MediaType.APPLICATION_JSON
                    content = body()
                }
            else ->
                mockMvc.post(path) {
                    header("Authorization", "Bearer $TOKEN")
                    contentType = MediaType.APPLICATION_JSON
                    content = body()
                }
        }

    private inline fun <reified T> decode(result: ResultActionsDsl): T =
        BitsJson.decodeFromString(
            result
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun link(
        discordUserId: String,
        wareraUserId: String,
    ) {
        jdbc.update(
            "INSERT INTO account_link (discord_user_id, warera_user_id, warera_username, verified_at) VALUES (?, ?, ?, now())",
            discordUserId,
            wareraUserId,
            wareraUserId,
        )
    }

    private fun streamEvents(): List<BitsEvent> =
        redisTemplate
            .opsForStream<String, String>()
            .read(StreamOffset.fromStart(EventStream.KEY))
            .orEmpty()
            .map { decodeEvent(it.value[EventStream.PAYLOAD_FIELD]!!) }

    private fun <T> ResultActionsDsl.read(path: String): T = JsonPath.read(andReturn().response.contentAsString, path)

    private fun session(user: SessionUser) = sessionTokens.cookie(user).let { Cookie(it.name, it.value) }

    private companion object {
        val PNG_SIGNATURE = listOf<Byte>(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    }
}

private const val TOKEN = "test-service-token-at-least-32-chars"
