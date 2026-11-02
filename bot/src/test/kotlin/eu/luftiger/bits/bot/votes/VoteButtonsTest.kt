package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.bot.backend.VoteBackend
import eu.luftiger.bits.bot.commands.Replies
import eu.luftiger.bits.bot.commands.buttonClick
import eu.luftiger.bits.bot.commands.languages
import eu.luftiger.bits.shared.CastVoteResponse
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.VoteChoice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.Role
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class VoteButtonsTest {
    private val backend = mockk<VoteBackend>()

    @Test
    fun `members without the role are turned away`() =
        runTest {
            val replies = Replies()
            val event = buttonClick("vote:7:YES:r-congress", replies)
            every { event.member!!.roles } returns emptyList()

            VoteButtons(backend, backgroundScope, languages()).onButtonInteraction(event)
            runCurrent()

            assertEquals(listOf("Abstimmen dürfen nur Mitglieder mit der Rolle <@&r-congress>."), replies.messages)
            coVerify(exactly = 0) { backend.castVote(any(), any(), any()) }
        }

    @Test
    fun `members without a link are sent to verify`() =
        runTest {
            coEvery { backend.castVote(7, "42", VoteChoice.NO) } returns CastVoteResponse.NotLinked
            val replies = Replies()

            VoteButtons(backend, backgroundScope, languages()).onButtonInteraction(clickWithRole("vote:7:NO:r-congress", replies))
            runCurrent()

            assertContains(replies.messages.single(), "`/verify`")
        }

    @Test
    fun `the answer says whether the vote was new, the same or changed`() {
        assertContains(
            VoteButtons.castMessage(CastVoteResponse.Cast(previous = null), VoteChoice.YES, Language.GERMAN),
            "gespeichert: **Ja**",
        )
        assertContains(VoteButtons.castMessage(CastVoteResponse.Cast(VoteChoice.YES), VoteChoice.YES, Language.GERMAN), "schon mit **Ja**")
        assertContains(
            VoteButtons.castMessage(CastVoteResponse.Cast(VoteChoice.YES), VoteChoice.ABSTAIN, Language.GERMAN),
            "**Enthaltung** statt Ja",
        )
        assertContains(VoteButtons.castMessage(CastVoteResponse.Closed, VoteChoice.YES, Language.GERMAN), "beendet")
    }

    @Test
    fun `other buttons are left alone`() =
        runTest {
            val replies = Replies()

            VoteButtons(backend, backgroundScope, languages()).onButtonInteraction(buttonClick("nickname:on", replies))
            runCurrent()

            assertEquals(emptyList(), replies.messages)
        }

    private fun clickWithRole(
        componentId: String,
        replies: Replies,
    ) = buttonClick(componentId, replies).also { event ->
        val role = mockk<Role> { every { id } returns "r-congress" }
        every { event.member!!.roles } returns listOf(role)
        every { event.member!!.id } returns "42"
    }
}
