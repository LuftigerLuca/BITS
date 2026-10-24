package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleChangeOutcome
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.RoleSyncReport
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import java.util.concurrent.CompletableFuture
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** JDA is mocked: the guild knows [roles] and [members], and records every role change it is asked for. */
class RoleSyncHandlerTest {
    private val jda = mockk<JDA>()
    private val guild = mockk<Guild>()
    private val self = mockk<SelfMember>()
    private val roles = mutableMapOf<String, Role>()
    private val members = mutableMapOf<String, Member>()
    private val memberRoles = mutableMapOf<String, MutableSet<String>>()

    /** Changes the guild applied, as `+role@member` or `-role@member`. */
    private val applied = mutableListOf<String>()
    private val failing = mutableSetOf<String>()
    private val reasons = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        every { jda.getGuildById("100") } returns guild
        every { guild.selfMember } returns self
        every { guild.members } answers { members.values.toList() }
        every { guild.getRoleById(any<String>()) } answers { roles[firstArg()] }
        every { guild.getMemberById(any<String>()) } answers { members[firstArg()] }
        every { self.canInteract(any<Role>()) } answers { firstArg<Role>().id != "above-bot" }
        every { guild.addRoleToMember(any<Member>(), any()) } answers { change("+", firstArg(), secondArg()) }
        every { guild.removeRoleFromMember(any<Member>(), any()) } answers { change("-", firstArg(), secondArg()) }
        listOf("citizen", "veteran", "above-bot").forEach(::role)
    }

    @Test
    fun `grants and takes managed roles with the reason for the audit log`() =
        runTest {
            member("1", "veteran", "unmanaged")

            handler().handle(sync(mapOf("1" to grants("citizen"))))

            assertEquals(listOf("+citizen@1", "-veteran@1"), applied)
            assertEquals(listOf("BITS: citizen", REMOVE_REASON), reasons)
        }

    @Test
    fun `members not in the cache are fetched, those who left are skipped`() =
        runTest {
            fetchable(member("1"))
            gone("2")

            handler().handle(sync(mapOf("1" to grants("citizen"), "2" to grants("citizen")), discordUserIds = listOf("1", "2")))

            assertEquals(listOf("+citizen@1"), applied)
        }

    @Test
    fun `roles that are gone or above the bot are skipped`() =
        runTest {
            member("1")

            handler().handle(
                sync(mapOf("1" to grants("citizen", "above-bot", "deleted")), managed = listOf("citizen", "above-bot", "deleted")),
            )

            assertEquals(listOf("+citizen@1"), applied)
        }

    @Test
    fun `a failing change does not stop the others`() =
        runTest {
            member("1")
            member("2")
            failing += "+citizen@1"

            handler().handle(sync(mapOf("1" to grants("citizen"), "2" to grants("citizen"))))

            assertEquals(listOf("+citizen@2"), applied)
        }

    @Test
    fun `a full sync that would take a role from most of its holders takes it from nobody`() =
        runTest {
            (1..10).forEach { member("$it", "veteran") }

            handler().handle(sync(emptyMap()))

            assertEquals(emptyList(), applied)
        }

    @Test
    fun `members already synced by a newer sync are left alone`() =
        runTest {
            member("1")
            val handler = handler()

            handler.handle(sync(mapOf("1" to grants("citizen")), discordUserIds = listOf("1"), occurredAt = 2))
            memberRoles.getValue("1").clear()
            handler.handle(sync(mapOf("1" to grants("citizen")), occurredAt = 1))

            assertEquals(listOf("+citizen@1"), applied)
        }

    @Test
    fun `reports what it changed, skipped and failed with the names of members and roles`() =
        runTest {
            member("1", "veteran")
            member("2")
            failing += "+citizen@2"
            val reports = mutableListOf<Pair<Long, RoleSyncReport>>()

            RoleSyncHandler(jda, "100") { syncId, report -> reports += syncId to report }.handle(
                sync(
                    mapOf("1" to grants("citizen", "above-bot"), "2" to grants("citizen")),
                    managed = listOf("citizen", "veteran", "above-bot"),
                    syncId = 7,
                ),
            )

            val (syncId, report) = reports.single()
            assertEquals(7, syncId)
            assertEquals(2, report.memberCount)
            assertEquals(
                listOf(
                    "GRANTED citizen (citizen) Member 1",
                    "REMOVED veteran (veteran) Member 1",
                    "SKIPPED above-bot (above-bot) Member 1",
                    "FAILED citizen (citizen) Member 2",
                ),
                report.changes.map { "${it.outcome} ${it.roleId} (${it.roleName}) ${it.memberName}" },
            )
            assertEquals("Discord hat die Änderung abgelehnt: Missing permissions", report.changes.last().reason)
        }

    @Test
    fun `roles held back by the removal limit are reported as skipped`() =
        runTest {
            (1..10).forEach { member("$it", "veteran") }
            val reports = mutableListOf<RoleSyncReport>()

            RoleSyncHandler(jda, "100") { _, report -> reports += report }.handle(sync(emptyMap(), syncId = 1))

            assertEquals(List(10) { RoleChangeOutcome.SKIPPED }, reports.single().changes.map { it.outcome })
        }

    @Test
    fun `syncs from a backend without system log are not reported`() =
        runTest {
            member("1")
            val reports = mutableListOf<RoleSyncReport>()

            RoleSyncHandler(jda, "100") { _, report -> reports += report }.handle(sync(mapOf("1" to grants("citizen"))))

            assertEquals(emptyList(), reports)
        }

    private fun handler() = RoleSyncHandler(jda, "100")

    private fun sync(
        members: Map<String, MemberRoles>,
        discordUserIds: List<String>? = null,
        managed: List<String> = listOf("citizen", "veteran"),
        occurredAt: Long = 1,
        syncId: Long? = null,
    ) = RoleSyncEvent(
        managedRoleIds = managed,
        discordUserIds = discordUserIds,
        members = members,
        occurredAt = occurredAt,
        syncId = syncId,
    )

    private fun grants(vararg roleIds: String) = MemberRoles(grants = roleIds.map { RoleGrant(it, "BITS: $it") })

    private fun role(id: String) {
        roles[id] = roleMock(id)
    }

    private fun roleMock(id: String): Role {
        val role = mockk<Role>()
        every { role.id } returns id
        every { role.name } returns id
        return role
    }

    private fun member(
        id: String,
        vararg roleIds: String,
    ): Member {
        memberRoles[id] = roleIds.toMutableSet()
        val member = mockk<Member>()
        every { member.id } returns id
        every { member.effectiveName } returns "Member $id"
        every { member.roles } answers { memberRoles.getValue(id).map { roleId: String -> roles[roleId] ?: roleMock(roleId) } }
        members[id] = member
        return member
    }

    /** Known to Discord, but not in the bot's cache. */
    private fun fetchable(member: Member) {
        val id = member.id
        members.remove(id)
        val action = mockk<CacheRestAction<Member>> { every { submit() } returns CompletableFuture.completedFuture(member) }
        every { guild.retrieveMemberById(id) } returns action
    }

    private fun gone(id: String) {
        val error = mockk<ErrorResponseException> { every { errorResponse } returns ErrorResponse.UNKNOWN_MEMBER }
        val action = mockk<CacheRestAction<Member>> { every { submit() } returns CompletableFuture.failedFuture(error) }
        every { guild.retrieveMemberById(id) } returns action
    }

    private fun change(
        sign: String,
        member: Member,
        role: Role,
    ): AuditableRestAction<Void> {
        val key = "$sign${role.id}@${member.id}"
        val action = mockk<AuditableRestAction<Void>>()
        every { action.reason(any()) } answers {
            reasons += firstArg<String>()
            action
        }
        every { action.submit() } answers {
            if (key in failing) {
                CompletableFuture.failedFuture(IllegalStateException("Missing permissions"))
            } else {
                applied += key
                CompletableFuture.completedFuture(null)
            }
        }
        return action
    }
}
