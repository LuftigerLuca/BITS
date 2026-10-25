package eu.luftiger.bits.bot.members

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.DiscordRoleDto
import eu.luftiger.bits.shared.NicknamePreferenceResponse
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.shared.ProfileResponse
import eu.luftiger.bits.shared.ReportedMember
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.RoleSyncReport
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.UndeliveredEventReport
import eu.luftiger.bits.shared.UnlinkAccountResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberReporterTest {
    private val backend = FakeBackend()

    @Test
    fun `the list leaves out bots`() =
        runTest {
            val guild = guild("100", member("1"), member("2"), member("9", bot = true))

            MemberReporter("100", backend, backgroundScope).report(guild)
            runCurrent()

            assertEquals(listOf(listOf("1", "2")), backend.lists)
        }

    @Test
    fun `the list carries the names and roles of the members`() =
        runTest {
            val guild = guild("100", member("1", roleIds = listOf("r-citizen", "r-veteran")))

            MemberReporter("100", backend, backgroundScope).report(guild)
            runCurrent()

            assertEquals(listOf(ReportedMember("1", "Member 1", listOf("r-citizen", "r-veteran"))), backend.reports.single())
        }

    @Test
    fun `role changes send the list again once they are over`() =
        runTest {
            val guild = guild("100", member("1"))
            val reporter = MemberReporter("100", backend, backgroundScope)

            repeat(3) { reporter.onGuildMemberRoleAdd(roleAdded(guild, user("1"))) }
            reporter.onGuildMemberRoleRemove(roleRemoved(guild, user("1")))
            reporter.onGuildMemberRoleAdd(roleAdded(guild, user("9", bot = true)))
            advanceTimeBy(4_000)
            assertEquals(emptyList(), backend.lists)

            advanceTimeBy(2_000)
            assertEquals(listOf(listOf("1")), backend.lists)
        }

    @Test
    fun `the list waits until all members are loaded`() =
        runTest {
            val guild = guild("100", member("1"))
            every { guild.isLoaded } returns false
            MemberReporter("100", backend, backgroundScope).report(guild)
            runCurrent()
            assertEquals(emptyList(), backend.lists)

            every { guild.isLoaded } returns true
            // advanceUntilIdle does not wait for the background scope.
            advanceTimeBy(3_000)

            assertEquals(listOf(listOf("1")), backend.lists)
        }

    @Test
    fun `a list the backend does not take is sent again until it goes through`() =
        runTest {
            backend.failures = 2
            val reporter = MemberReporter("100", backend, backgroundScope)

            reporter.report { listOf(ReportedMember("1", "Luca", emptyList())) }
            advanceTimeBy(600_000)

            assertEquals(listOf(listOf("1")), backend.lists)
            assertEquals(3, backend.attempts)
        }

    @Test
    fun `joins and departures are reported, with the name of who left`() =
        runTest {
            val guild = guild("100")
            val reporter = MemberReporter("100", backend, backgroundScope)

            reporter.onGuildMemberJoin(joined(guild, user("1")))
            reporter.onGuildMemberRemove(left(guild, user("2", name = "Luca")))
            runCurrent()

            assertEquals(listOf("joined 1", "left 2 Luca"), backend.changes)
        }

    @Test
    fun `bots and other guilds are not reported`() =
        runTest {
            val reporter = MemberReporter("100", backend, backgroundScope)

            reporter.onGuildMemberJoin(joined(guild("100"), user("9", bot = true)))
            reporter.onGuildMemberRemove(left(guild("100"), user("9", bot = true)))
            reporter.onGuildMemberJoin(joined(guild("200"), user("1")))
            reporter.report(guild("200", member("1")))
            runCurrent()

            assertEquals(emptyList(), backend.changes)
            assertEquals(emptyList(), backend.lists)
        }

    private fun guild(
        id: String,
        vararg members: Member,
    ): Guild {
        val guild = mockk<Guild>()
        every { guild.id } returns id
        every { guild.isLoaded } returns true
        every { guild.members } returns members.toList()
        return guild
    }

    private fun member(
        id: String,
        bot: Boolean = false,
        roleIds: List<String> = emptyList(),
    ): Member {
        val member = mockk<Member>()
        val user = user(id, bot)
        every { member.id } returns id
        every { member.user } returns user
        every { member.effectiveName } returns "Member $id"
        every { member.roles } returns
            roleIds.map { roleId ->
                mockk<Role>().also { every { it.id } returns roleId }
            }
        return member
    }

    private fun roleAdded(
        guild: Guild,
        user: User,
    ): GuildMemberRoleAddEvent {
        val event = mockk<GuildMemberRoleAddEvent>()
        every { event.guild } returns guild
        every { event.user } returns user
        return event
    }

    private fun roleRemoved(
        guild: Guild,
        user: User,
    ): GuildMemberRoleRemoveEvent {
        val event = mockk<GuildMemberRoleRemoveEvent>()
        every { event.guild } returns guild
        every { event.user } returns user
        return event
    }

    private fun user(
        id: String,
        bot: Boolean = false,
        name: String = "User $id",
    ): User {
        val user = mockk<User>()
        every { user.id } returns id
        every { user.isBot } returns bot
        every { user.effectiveName } returns name
        return user
    }

    private fun joined(
        guild: Guild,
        user: User,
    ): GuildMemberJoinEvent {
        val event = mockk<GuildMemberJoinEvent>()
        every { event.guild } returns guild
        every { event.user } returns user
        return event
    }

    /** The member is usually no longer cached when it leaves, so the name comes from the user. */
    private fun left(
        guild: Guild,
        user: User,
    ): GuildMemberRemoveEvent {
        val event = mockk<GuildMemberRemoveEvent>()
        every { event.guild } returns guild
        every { event.user } returns user
        every { event.member } returns null
        return event
    }

    private class FakeBackend : BackendClient {
        val reports = mutableListOf<List<ReportedMember>>()
        val lists get() = reports.map { report -> report.map { it.discordUserId } }
        val changes = mutableListOf<String>()
        var failures = 0
        var attempts = 0

        override suspend fun reportMembers(members: List<ReportedMember>) {
            attempts++
            if (failures-- > 0) error("backend down")
            reports += members
        }

        override suspend fun reportMemberJoined(discordUserId: String) {
            changes += "joined $discordUserId"
        }

        override suspend fun reportMemberLeft(
            discordUserId: String,
            memberName: String,
        ) {
            changes += "left $discordUserId $memberName"
        }

        override suspend fun ping(discordUserId: String): PingResponse = TODO()

        override suspend fun startVerification(
            discordUserId: String,
            wareraUsername: String,
        ): StartVerificationResponse = TODO()

        override suspend fun checkVerification(
            discordUserId: String,
            code: String,
            discordName: String?,
        ): CheckVerificationResponse = TODO()

        override suspend fun unlinkAccount(discordUserId: String): UnlinkAccountResponse = TODO()

        override suspend fun setNicknamePreference(
            discordUserId: String,
            enabled: Boolean,
        ): NicknamePreferenceResponse = TODO()

        override suspend fun profile(discordUserId: String): ProfileResponse = TODO()

        override suspend fun explainRoles(discordUserId: String): RoleExplanationResponse = TODO()

        override suspend fun reportRoles(roles: List<DiscordRoleDto>) = TODO()

        override suspend fun reportRoleSync(
            syncId: Long,
            report: RoleSyncReport,
        ) = TODO()

        override suspend fun reportUndeliveredEvent(report: UndeliveredEventReport) = TODO()
    }
}
