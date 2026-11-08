package eu.luftiger.bits.bot.tops

import eu.luftiger.bits.bot.backend.TopBackend
import eu.luftiger.bits.bot.commands.Replies
import eu.luftiger.bits.bot.commands.languages
import eu.luftiger.bits.bot.commands.slashCommand
import eu.luftiger.bits.shared.TopActionReport
import eu.luftiger.bits.shared.TopPermissionResponse
import eu.luftiger.bits.shared.VoteActor
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
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TopCommandTest {
    private val backend = mockk<TopBackend>()

    @Test
    fun `outside a forum post the command is refused without asking the backend`() =
        runTest {
            val replies = Replies()

            TopCloseCommand(Tops(backend), backgroundScope, languages(), post = { null })
                .onSlashCommandInteraction(command("topclose", replies))
            runCurrent()

            assertEquals(listOf("Das geht nur in einem Post eines TOP-Forums."), replies.messages)
            coVerify(exactly = 0) { backend.topPermission(any(), any()) }
        }

    @Test
    fun `closing sends the member with their roles and the options`() =
        runTest {
            val actor = slot<VoteActor>()
            val report = slot<TopActionReport>()
            coEvery { backend.topPermission(capture(actor), "f-1") } returns TopPermissionResponse.Allowed(null)
            coEvery { backend.reportTopAction(capture(report)) } returns Unit
            val post = mockk<TopPost>(relaxed = true)
            every { post.forumId } returns "f-1"
            every { post.locked } returns false
            every { post.botMayManage } returns true
            every { post.appliedTagIds } returns emptyList()
            val replies = Replies()

            TopCloseCommand(Tops(backend), backgroundScope, languages(), post = { post })
                .onSlashCommandInteraction(command("topclose", replies, mapOf("reason" to "Vertagt"), archive = true))
            runCurrent()

            assertContains(replies.messages.single(), "geschlossen und archiviert")
            assertEquals(listOf("r-chair"), actor.captured.roleIds)
            assertEquals("Vertagt" to true, report.captured.reason to report.captured.archived)
            coVerify { post.send("TOP geschlossen von <@42>.\nGrund: Vertagt") }
        }

    @Test
    fun `other commands are left alone`() =
        runTest {
            val replies = Replies()

            TopOpenCommand(Tops(backend), backgroundScope, languages(), post = { null })
                .onSlashCommandInteraction(command("topclose", replies))
            runCurrent()

            assertEquals(emptyList(), replies.messages)
        }

    private fun command(
        name: String,
        replies: Replies,
        options: Map<String, String> = emptyMap(),
        archive: Boolean? = null,
    ) = slashCommand(name, replies, options).also { event ->
        val member = mockk<Member>(relaxed = true)
        every { member.id } returns "42"
        every { member.asMention } returns "<@42>"
        every { member.roles } returns listOf(mockk { every { id } returns "r-chair" })
        every { member.hasPermission(Permission.ADMINISTRATOR) } returns false
        every { event.member } returns member
        every { event.getOption("archive") } returns archive?.let { value -> mockk { every { asBoolean } returns value } }
    }
}
