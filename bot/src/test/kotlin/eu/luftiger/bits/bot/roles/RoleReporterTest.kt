package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.DiscordChannelDto
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
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.SelfMember
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class RoleReporterTest {
    private val role = DiscordRoleDto(id = "r1", name = "Veteran", color = null, position = 1, assignable = true)
    private val backend = FakeBackend()

    @Test
    fun `a report the backend does not take is sent again until it goes through`() =
        runTest {
            backend.failures = 2
            val reporter = RoleReporter("100", backend, backgroundScope)

            reporter.report { listOf(role) }
            // advanceUntilIdle does not wait for the background scope.
            advanceTimeBy(600_000)

            assertEquals(listOf(listOf(role)), backend.reports)
            assertEquals(3, backend.attempts)
        }

    @Test
    fun `a newer report replaces the one that waits for a retry`() =
        runTest {
            backend.failures = Int.MAX_VALUE
            val reporter = RoleReporter("100", backend, backgroundScope)

            reporter.report { listOf(role) }
            advanceTimeBy(3_000)
            backend.failures = 0
            reporter.report { listOf(role.copy(name = "Minister")) }
            // advanceUntilIdle does not wait for the background scope.
            advanceTimeBy(600_000)

            assertEquals(listOf(listOf(role.copy(name = "Minister"))), backend.reports)
        }

    @Test
    fun `a burst of changes is reported once, after things have been quiet`() =
        runTest {
            val reporter = RoleReporter("100", backend, backgroundScope)

            repeat(5) { i ->
                reporter.report { listOf(role.copy(position = i)) }
                advanceTimeBy(500)
            }
            advanceTimeBy(1_000)
            assertEquals(emptyList(), backend.reports)
            advanceTimeBy(2_000)

            assertEquals(listOf(listOf(role.copy(position = 4))), backend.reports)
        }

    @Test
    fun `roles are reported with their color and whether the bot can give them`() =
        runTest {
            val guild = guild("100")
            every { guild.roles } returns
                listOf(
                    role(guild, "everyone", position = 0, public = true),
                    role(guild, "booster", position = 1, managed = true),
                    role(guild, "veteran", position = 2, color = 0x00FF00),
                    role(guild, "admin", position = 9, aboveBot = true),
                )
            val reporter = RoleReporter("100", backend, backgroundScope)

            reporter.report(guild)
            advanceTimeBy(3_000)

            assertEquals(
                listOf(
                    DiscordRoleDto("everyone", "everyone", color = null, position = 0, assignable = false),
                    DiscordRoleDto("booster", "booster", color = null, position = 1, assignable = false),
                    DiscordRoleDto("veteran", "veteran", color = 0x00FF00, position = 2, assignable = true),
                    DiscordRoleDto("admin", "admin", color = null, position = 9, assignable = false),
                ),
                backend.reports.single(),
            )
        }

    @Test
    fun `other guilds are not reported`() =
        runTest {
            val other = guild("200")
            every { other.roles } returns emptyList()

            RoleReporter("100", backend, backgroundScope).report(other)
            advanceTimeBy(3_000)

            assertEquals(emptyList(), backend.reports)
        }

    @Test
    fun `role changes of members are reported only for the bot itself`() =
        runTest {
            val guild = guild("100")
            every { guild.roles } returns emptyList()
            val reporter = RoleReporter("100", backend, backgroundScope)

            reporter.onGuildMemberRoleAdd(roleAdded(guild, mockk()))
            advanceTimeBy(3_000)
            assertEquals(0, backend.attempts)

            val self = guild.selfMember
            reporter.onGuildMemberRoleAdd(roleAdded(guild, self))
            advanceTimeBy(3_000)
            assertEquals(1, backend.attempts)
        }

    private fun guild(id: String): Guild {
        val guild = mockk<Guild>()
        val self = mockk<SelfMember>()
        every { guild.id } returns id
        every { guild.selfMember } returns self
        every { self.canInteract(any<Role>()) } returns true
        return guild
    }

    private fun role(
        guild: Guild,
        id: String,
        position: Int,
        color: Int = Role.DEFAULT_COLOR_RAW,
        public: Boolean = false,
        managed: Boolean = false,
        aboveBot: Boolean = false,
    ): Role {
        val role = mockk<Role>()
        every { role.id } returns id
        every { role.name } returns id
        every { role.position } returns position
        every { role.colors } returns mockk { every { primaryRaw } returns color }
        every { role.isPublicRole } returns public
        every { role.isManaged } returns managed
        every { role.guild } returns guild
        val self = guild.selfMember
        if (aboveBot) every { self.canInteract(role) } returns false
        return role
    }

    private fun roleAdded(
        guild: Guild,
        member: Member,
    ): GuildMemberRoleAddEvent {
        val event = mockk<GuildMemberRoleAddEvent>()
        every { event.guild } returns guild
        every { event.member } returns member
        return event
    }

    private class FakeBackend : BackendClient {
        val reports = mutableListOf<List<DiscordRoleDto>>()
        var failures = 0
        var attempts = 0

        override suspend fun reportRoles(roles: List<DiscordRoleDto>) {
            attempts++
            if (failures-- > 0) error("backend down")
            reports += roles
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

        override suspend fun reportRoleSync(
            syncId: Long,
            report: RoleSyncReport,
        ) = TODO()

        override suspend fun reportUndeliveredEvent(report: UndeliveredEventReport) = TODO()

        override suspend fun reportChannels(channels: List<DiscordChannelDto>) = TODO()

        override suspend fun reportMembers(members: List<ReportedMember>) = TODO()

        override suspend fun reportMemberJoined(discordUserId: String) = TODO()

        override suspend fun reportMemberLeft(
            discordUserId: String,
            memberName: String,
        ) = TODO()
    }
}
