package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.NicknamePreferenceResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NicknameCommandTest {
    private val backend = mockk<BackendClient>()

    @Test
    fun `turning it on names the WarEra name that becomes the nickname`() =
        runTest {
            coEvery { backend.setNicknamePreference("42", true) } returns NicknamePreferenceResponse(true, "_Luftiger_")
            val replies = Replies()
            val event = slashCommand("nickname", replies, mapOf("modus" to "an"))
            renamable(event.member!!, true)

            NicknameCommand(backend, backgroundScope).onSlashCommandInteraction(event)
            runCurrent()

            val message = replies.messages.single()
            assertContains(message, "**\\_Luftiger\\_** als Nickname")
            assertFalse("Hinweis" in message)
        }

    @Test
    fun `turning it off leaves the nickname`() =
        runTest {
            coEvery { backend.setNicknamePreference("42", false) } returns NicknamePreferenceResponse(false, "Luftiger")
            val replies = Replies()

            NicknameCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand("nickname", replies, mapOf("modus" to "aus")))
            runCurrent()

            assertContains(replies.messages.single(), "Er bleibt, wie er ist")
        }

    @Test
    fun `the button after linking turns it on`() =
        runTest {
            coEvery { backend.setNicknamePreference("42", true) } returns NicknamePreferenceResponse(true, "Luftiger")
            val replies = Replies()

            NicknameCommand(backend, backgroundScope).onButtonInteraction(buttonClick(NicknameCommand.OPT_IN_BUTTON, replies))
            runCurrent()

            coVerify { backend.setNicknamePreference("42", true) }
            assertEquals(1, replies.messages.size)
        }

    @Test
    fun `says when the bot cannot rename the member or they are not linked yet`() {
        val message = NicknameCommand.preferenceMessage(NicknamePreferenceResponse(true, null), renamable = false)

        assertContains(message, "Sobald du dich mit `/verify` verknüpft hast")
        assertContains(message, "Hinweis: Deinen Nickname kann ich gerade nicht ändern")
    }

    @Test
    fun `says BITS is not reachable when the backend fails`() =
        runTest {
            coEvery { backend.setNicknamePreference(any(), any()) } throws IllegalStateException("connection refused")
            val replies = Replies()

            NicknameCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand("nickname", replies, mapOf("modus" to "an")))
            runCurrent()

            assertEquals(listOf(BITS_UNAVAILABLE_MESSAGE), replies.messages)
        }

    private fun renamable(
        member: Member,
        renamable: Boolean,
    ) {
        every { member.guild.selfMember.hasPermission(Permission.NICKNAME_MANAGE) } returns renamable
        every { member.guild.selfMember.canInteract(member) } returns renamable
    }
}
