package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.bot.backend.VoteBackend
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import eu.luftiger.bits.shared.VoteResultDto
import eu.luftiger.bits.shared.VoteStartedEvent
import eu.luftiger.bits.shared.VoteState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import kotlin.test.Test

class VoteEventsTest {
    private val backend = mockk<VoteBackend>(relaxUnitFun = true)
    private val jda = mockk<JDA>(relaxed = true)
    private val result = VoteResultDto("Steuern?", parties = emptyList(), nonVoters = emptyList(), image = "", posted = false)
    private val vote = VoteDto(7, "Steuern?", "r-congress", "c-votes", endsAt = null, state = VoteState.OPEN, messageId = null)

    @Test
    fun `a vote that is posted already is not posted again`() =
        runTest {
            coEvery { backend.vote(7) } returns vote.copy(messageId = "m1")

            VoteEvents(jda, "100", backend).onStarted(VoteStartedEvent(7, occurredAt = 0))

            verify(exactly = 0) { jda.getGuildById(any<String>()) }
            coVerify(exactly = 0) { backend.reportVoteMessage(any(), any()) }
        }

    @Test
    fun `a result that is posted already is not posted again`() =
        runTest {
            coEvery { backend.voteResult(7) } returns result.copy(posted = true)

            VoteEvents(jda, "100", backend).onEvaluated(VoteEvaluatedEvent(7, occurredAt = 0))

            coVerify(exactly = 0) { backend.vote(any()) }
            coVerify(exactly = 0) { backend.markVoteResultPosted(any()) }
        }

    @Test
    fun `a result whose channel is gone is given up, since asking again would not help`() =
        runTest {
            coEvery { backend.voteResult(7) } returns result
            coEvery { backend.vote(7) } returns vote.copy(state = VoteState.EVALUATED)
            val guild = mockk<Guild> { every { getGuildChannelById("c-votes") } returns null }
            every { jda.getGuildById("100") } returns guild

            VoteEvents(jda, "100", backend).onEvaluated(VoteEvaluatedEvent(7, occurredAt = 0))

            coVerify { backend.markVoteResultPosted(7) }
        }
}
