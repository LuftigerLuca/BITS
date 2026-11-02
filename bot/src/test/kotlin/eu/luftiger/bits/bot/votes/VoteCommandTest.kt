package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.bot.backend.VoteBackend
import eu.luftiger.bits.bot.commands.Replies
import eu.luftiger.bits.bot.commands.languages
import eu.luftiger.bits.bot.commands.slashCommand
import eu.luftiger.bits.shared.CloseVoteResponse
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.StartVoteRequest
import eu.luftiger.bits.shared.StartVoteResponse
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteThreshold
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class VoteCommandTest {
    private val backend = mockk<VoteBackend>()
    private val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `starting sends the question, role, channel and end with the member's roles`() =
        runTest {
            val request = slot<StartVoteRequest>()
            coEvery { backend.startVote(capture(request)) } returns StartVoteResponse.Started(7)
            val replies = Replies()

            VoteCommand(backend, backgroundScope, languages(), clock).onSlashCommandInteraction(
                start(
                    replies,
                    mapOf(
                        "question" to "Steuern?",
                        "end" to "3d",
                    ),
                ),
            )
            runCurrent()

            assertContains(replies.messages.single(), "Die Abstimmung ist gestartet")
            assertEquals("Steuern?", request.captured.question)
            assertEquals("r-congress" to "c-votes", request.captured.roleId to request.captured.channelId)
            assertEquals(Instant.parse("2026-01-04T12:00:00Z").toEpochMilli(), request.captured.endsAt)
            assertEquals(listOf("r-speaker"), request.captured.actor.roleIds)
            assertEquals(null, request.captured.threshold)
        }

    @Test
    fun `starting sends the threshold at which the vote ends early, for Yes unless told otherwise`() =
        runTest {
            val request = slot<StartVoteRequest>()
            coEvery { backend.startVote(capture(request)) } returns StartVoteResponse.Started(7)
            val command = VoteCommand(backend, backgroundScope, languages(), clock)

            command.onSlashCommandInteraction(start(Replies(), mapOf("question" to "Steuern?", "end" to "offen", "threshold" to "50")))
            runCurrent()
            assertEquals(VoteThreshold(50, VoteChoice.YES), request.captured.threshold)
            assertEquals(null, request.captured.endsAt)

            command.onSlashCommandInteraction(start(Replies(), mapOf("question" to "Steuern?", "threshold" to "66", "choice" to "NO")))
            runCurrent()
            assertEquals(VoteThreshold(66, VoteChoice.NO), request.captured.threshold)
        }

    @Test
    fun `a threshold outside 1 to 99 percent or without percentage is explained, not sent`() =
        runTest {
            val command = VoteCommand(backend, backgroundScope, languages(), clock)
            val tooHigh = Replies()
            val choiceOnly = Replies()

            command.onSlashCommandInteraction(start(tooHigh, mapOf("question" to "?", "threshold" to "100")))
            command.onSlashCommandInteraction(start(choiceOnly, mapOf("question" to "?", "choice" to "NO")))
            runCurrent()

            assertEquals(listOf("Die Schwelle gibst du in Prozent von 1 bis 99 an, z. B. `50` für mehr als die Hälfte."), tooHigh.messages)
            assertEquals(listOf("Gib zur Stimme auch die Schwelle an, z. B. `threshold:50`, oder lass beides weg."), choiceOnly.messages)
            coVerify(exactly = 0) { backend.startVote(any()) }
        }

    @Test
    fun `an end it cannot read is explained, not sent`() =
        runTest {
            val replies = Replies()

            VoteCommand(
                backend,
                backgroundScope,
                languages(),
                clock,
            ).onSlashCommandInteraction(start(replies, mapOf("question" to "?", "end" to "morgen")))
            runCurrent()

            assertContains(replies.messages.single(), "Das Ende verstehe ich nicht")
            coVerify(exactly = 0) { backend.startVote(any()) }
        }

    @Test
    fun `members who may not manage votes are told so`() =
        runTest {
            coEvery { backend.closeVote(7, any(), cancel = true) } returns CloseVoteResponse.Forbidden
            val replies = Replies()
            val event = withMember(slashCommand("vote", replies, mapOf("vote" to "7")))
            every { event.subcommandName } returns "cancel"

            VoteCommand(backend, backgroundScope, languages(), clock).onSlashCommandInteraction(event)
            runCurrent()

            assertEquals(listOf(VoteCommand.forbiddenMessage(Language.GERMAN)), replies.messages)
        }

    @Test
    fun `a vote that ended already is not ended again`() =
        runTest {
            coEvery { backend.closeVote(7, any(), cancel = false) } returns CloseVoteResponse.NotOpen
            val replies = Replies()
            val event = withMember(slashCommand("vote", replies, mapOf("vote" to "7")))
            every { event.subcommandName } returns "end"

            VoteCommand(backend, backgroundScope, languages(), clock).onSlashCommandInteraction(event)
            runCurrent()

            assertEquals(listOf("Diese Abstimmung läuft nicht mehr."), replies.messages)
        }

    private fun start(
        replies: Replies,
        options: Map<String, String>,
    ): SlashCommandInteractionEvent {
        val event = withMember(slashCommand("vote", replies, options))
        val role = mockk<Role>(relaxed = true) { every { id } returns "r-congress" }
        val channel = mockk<GuildChannelUnion>(relaxed = true) { every { id } returns "c-votes" }
        every { event.subcommandName } returns "start"
        every { event.getOption("role") } returns mockk { every { asRole } returns role }
        every { event.getOption("channel") } returns mockk { every { asChannel } returns channel }
        every {
            event.member!!
                .guild.selfMember
                .hasPermission(channel, any<Collection<Permission>>())
        } returns true
        return event
    }

    private fun withMember(event: SlashCommandInteractionEvent): SlashCommandInteractionEvent {
        val speaker = mockk<Role> { every { id } returns "r-speaker" }
        every { event.member!!.id } returns "42"
        every { event.member!!.effectiveName } returns "Luca"
        every { event.member!!.roles } returns listOf(speaker)
        every { event.member!!.hasPermission(Permission.ADMINISTRATOR) } returns false
        return event
    }
}
