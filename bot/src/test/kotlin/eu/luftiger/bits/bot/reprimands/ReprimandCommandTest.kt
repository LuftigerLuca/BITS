package eu.luftiger.bits.bot.reprimands

import eu.luftiger.bits.bot.backend.ReprimandBackend
import eu.luftiger.bits.bot.commands.Replies
import eu.luftiger.bits.bot.commands.languages
import eu.luftiger.bits.bot.commands.slashCommand
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.ReprimandRequest
import eu.luftiger.bits.shared.ReprimandResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import kotlin.test.Test
import kotlin.test.assertEquals

class ReprimandCommandTest {
    private val backend = mockk<ReprimandBackend>()
    private val reprimands = Reprimands(backend, mockk(relaxed = true), { _, _ -> }, { error("unused") }, { "WarEra DE" }, { _, _ -> })

    @Test
    fun `sends both members with their roles and the reason`() =
        runTest {
            val request = slot<ReprimandRequest>()
            coEvery { backend.reprimand(capture(request)) } returns ReprimandResponse.Forbidden
            val replies = Replies()

            ReprimandCommand(reprimands, backgroundScope, languages())
                .onSlashCommandInteraction(command(replies, target = member("100", roles = listOf("r-citizen"), bot = true)))
            runCurrent()

            assertEquals(listOf(Reprimands.forbiddenMessage(Language.GERMAN)), replies.messages)
            assertEquals("42" to listOf("r-mod"), request.captured.actor.discordUserId to request.captured.actor.roleIds)
            assertEquals(listOf("r-citizen"), request.captured.target.roleIds)
            assertEquals(true, request.captured.target.bot)
            assertEquals("Spam", request.captured.reason)
        }

    @Test
    fun `a user who is not on the server is refused without asking the backend`() =
        runTest {
            val replies = Replies()

            ReprimandCommand(reprimands, backgroundScope, languages()).onSlashCommandInteraction(command(replies, target = null))
            runCurrent()

            assertEquals(listOf("Das Mitglied ist nicht auf dem Server."), replies.messages)
            coVerify(exactly = 0) { backend.reprimand(any()) }
        }

    private fun command(
        replies: Replies,
        target: Member?,
    ) = slashCommand("reprimand", replies, mapOf("reason" to "Spam")).also { event ->
        every { event.member } returns member("42", roles = listOf("r-mod"))
        every { event.getOption("member") } returns mockk { every { asMember } returns target }
    }

    private fun member(
        id: String,
        roles: List<String>,
        bot: Boolean = false,
    ): Member =
        mockk(relaxed = true) {
            every { this@mockk.id } returns id
            every { effectiveName } returns "Member $id"
            every { asMention } returns "<@$id>"
            every { this@mockk.roles } returns roles.map { role -> mockk { every { this@mockk.id } returns role } }
            every { hasPermission(Permission.ADMINISTRATOR) } returns false
            every { user.isBot } returns bot
        }
}
