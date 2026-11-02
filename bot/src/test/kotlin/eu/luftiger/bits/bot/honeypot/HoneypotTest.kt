package eu.luftiger.bits.bot.honeypot

import eu.luftiger.bits.bot.backend.HoneypotBackend
import eu.luftiger.bits.shared.HoneypotKickOutcome
import eu.luftiger.bits.shared.HoneypotKickReport
import eu.luftiger.bits.shared.HoneypotPauseReport
import eu.luftiger.bits.shared.HoneypotProblemReport
import eu.luftiger.bits.shared.HoneypotState
import eu.luftiger.bits.shared.HoneypotWarning
import eu.luftiger.bits.shared.HoneypotWarningReport
import eu.luftiger.bits.shared.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.entities.MessageEmbed
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoneypotTest {
    private class FakeBackend : HoneypotBackend {
        var state = HoneypotState(channelId = "trap")
        var down = false
        val kicks = mutableListOf<HoneypotKickReport>()
        val pauses = mutableListOf<HoneypotPauseReport>()
        val problems = mutableListOf<String>()

        override suspend fun honeypot(): HoneypotState {
            if (down) throw IOException("backend is down")
            return state
        }

        override suspend fun reportHoneypotWarning(report: HoneypotWarningReport) {
            if (down) throw IOException("backend is down")
            state = state.copy(warning = report.warning)
        }

        override suspend fun reportHoneypotKick(report: HoneypotKickReport) {
            kicks += report
        }

        override suspend fun reportHoneypotPause(report: HoneypotPauseReport) {
            if (down) throw IOException("backend is down")
            pauses += report
            state = state.copy(kicksPaused = true)
        }

        override suspend fun reportHoneypotProblem(report: HoneypotProblemReport) {
            problems += report.reason
        }
    }

    private class FakeGuild : HoneypotGuild {
        val deleted = mutableListOf<String>()
        val kicked = mutableListOf<String>()
        val posted = mutableListOf<String>()
        val embeds = mutableListOf<MessageEmbed>()
        val updated = mutableListOf<MessageEmbed>()
        var missing = false
        var channelGone = false
        var kickFails = false
        var deleteFails = false
        var canKick = true

        override suspend fun post(
            channelId: String,
            embed: MessageEmbed,
        ): String {
            if (channelGone) throw HoneypotChannelGone()
            posted += channelId
            embeds += embed
            return "warning-${posted.size}"
        }

        override suspend fun update(
            warning: HoneypotWarning,
            embed: MessageEmbed,
        ): Boolean {
            if (missing) return false
            updated += embed
            return true
        }

        override suspend fun delete(
            channelId: String,
            messageId: String,
        ) {
            if (deleteFails) throw IOException("refused")
            deleted += messageId
        }

        override fun mayKick() = canKick

        override suspend fun kick(discordUserId: String) {
            if (kickFails) throw IOException("refused")
            kicked += discordUserId
        }
    }

    private val backend = FakeBackend()
    private val guild = FakeGuild()
    private val directMessages = mutableListOf<String>()
    private val directMessageTexts = mutableListOf<String>()
    private var memberLanguage = Language.GERMAN
    private var dmFails = false
    private var time = 1_000_000L

    private fun CoroutineScope.honeypot() =
        Honeypot(
            backend,
            guild,
            { id, text ->
                if (dmFails) throw IOException("closed")
                directMessages += id
                directMessageTexts += text
            },
            { memberLanguage },
            { "WarEra" },
            { _, call -> launch { call() } },
            { time },
        )

    private fun message(
        author: String = "1",
        id: String = "m-$author",
        channel: String = "trap",
        kickable: Boolean = true,
    ) = HoneypotMessage(channel, id, author, "Name $author", kickable)

    @Test
    fun `a message is deleted, its author told and kicked, and the report has no content`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message())
            runCurrent()

            assertEquals(listOf("m-1"), guild.deleted)
            assertEquals(listOf("1"), directMessages)
            assertEquals(listOf("1"), guild.kicked)
            assertEquals(listOf(HoneypotKickReport("1", "Name 1", HoneypotKickOutcome.KICKED)), backend.kicks)
        }

    @Test
    fun `messages in other channels are left alone`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            assertFalse(honeypot.watches("general"))
            honeypot.handle(message(channel = "general"))
            runCurrent()

            assertEquals(emptyList(), guild.deleted)
            assertEquals(emptyList(), guild.kicked)
        }

    @Test
    fun `nothing is watched until the backend has answered`() =
        runTest {
            backend.down = true
            val honeypot = honeypot()
            honeypot.refresh()

            assertFalse(honeypot.watches("trap"))
        }

    @Test
    fun `an undeliverable direct message does not stop the kick and is reported`() =
        runTest {
            dmFails = true
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message())
            runCurrent()

            assertEquals(listOf("1"), guild.kicked)
            assertEquals(Honeypot.DIRECT_MESSAGE_FAILED, backend.kicks.single().reason)
        }

    @Test
    fun `a message that cannot be deleted does not stop the kick and is reported`() =
        runTest {
            guild.deleteFails = true
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message())
            runCurrent()

            assertEquals(listOf("1"), guild.kicked)
            assertEquals(Honeypot.MESSAGE_NOT_DELETED, backend.kicks.single().reason)
        }

    @Test
    fun `a member above the bot only loses the message`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message(kickable = false))
            runCurrent()

            assertEquals(listOf("m-1"), guild.deleted)
            assertEquals(emptyList(), guild.kicked)
            assertEquals(emptyList(), directMessages)
            assertEquals(HoneypotKickReport("1", "Name 1", HoneypotKickOutcome.SKIPPED, Honeypot.MEMBER_ABOVE_BOT), backend.kicks.single())
        }

    @Test
    fun `without the permission to kick, nobody gets a direct message about a kick`() =
        runTest {
            guild.canKick = false
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message())
            runCurrent()

            assertEquals(emptyList(), directMessages)
            assertEquals(HoneypotKickReport("1", "Name 1", HoneypotKickOutcome.FAILED, Honeypot.NO_KICK_PERMISSION), backend.kicks.single())
        }

    @Test
    fun `a refused kick is reported as failed`() =
        runTest {
            guild.kickFails = true
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message())
            runCurrent()

            assertEquals(HoneypotKickOutcome.FAILED, backend.kicks.single().outcome)
            assertEquals(Honeypot.KICK_REFUSED, backend.kicks.single().reason)
        }

    @Test
    fun `several messages of one account at once count as one kick`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message(id = "a"))
            honeypot.handle(message(id = "b"))
            runCurrent()

            assertEquals(listOf("a", "b"), guild.deleted)
            assertEquals(listOf("1"), guild.kicked)
            assertEquals(1, backend.kicks.size)
        }

    @Test
    fun `the eleventh case within ten minutes only loses the message, until an admin resumes`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            (1..Honeypot.MAX_KICKS).forEach { honeypot.handle(message(author = "$it")) }
            honeypot.handle(message(author = "eleven"))
            runCurrent()

            assertEquals(Honeypot.MAX_KICKS, guild.kicked.size)
            assertEquals(Honeypot.MAX_KICKS + 1, guild.deleted.size)
            assertEquals(
                HoneypotKickReport("eleven", "Name eleven", HoneypotKickOutcome.SKIPPED, Honeypot.KICKS_PAUSED),
                backend.kicks.last(),
            )

            // The answer of the backend keeps the pause, after a restart as well.
            honeypot.refresh()
            assertEquals(listOf(HoneypotPauseReport("trap", 10, 10)), backend.pauses)
            honeypot.handle(message(author = "twelve"))
            runCurrent()
            assertEquals(Honeypot.MAX_KICKS, guild.kicked.size)
            val restarted = honeypot()
            restarted.refresh()
            restarted.handle(message(author = "thirteen"))
            runCurrent()
            assertEquals(Honeypot.MAX_KICKS, guild.kicked.size)

            backend.state = backend.state.copy(kicksPaused = false)
            honeypot.refresh()
            honeypot.handle(message(author = "fourteen"))
            runCurrent()
            assertEquals("fourteen", guild.kicked.last())
            assertEquals(1, backend.pauses.size)
        }

    @Test
    fun `a pause reached during an outage holds and reaches the backend however long the outage lasts`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()
            backend.down = true

            (1..Honeypot.MAX_KICKS).forEach { honeypot.handle(message(author = "$it")) }
            repeat(3) { honeypot.refresh() }
            honeypot.handle(message(author = "eleven"))
            runCurrent()
            assertEquals(Honeypot.MAX_KICKS, guild.kicked.size)
            assertEquals(emptyList(), backend.pauses)

            backend.down = false
            honeypot.refresh()
            honeypot.refresh()
            assertEquals(listOf(HoneypotPauseReport("trap", 10, 10)), backend.pauses)
            assertTrue(backend.state.kicksPaused)
        }

    @Test
    fun `kicks spread over more than the window do not pause`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            repeat(Honeypot.MAX_KICKS * 2) {
                honeypot.handle(message(author = "$it"))
                time += Honeypot.WINDOW_MILLIS / (Honeypot.MAX_KICKS - 1) + 1
            }
            runCurrent()

            assertEquals(emptyList(), backend.pauses)
            assertEquals(Honeypot.MAX_KICKS * 2, guild.kicked.size)
        }

    @Test
    fun `while the backend is down, the last channel stays watched`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()
            backend.down = true
            honeypot.refresh()

            assertTrue(honeypot.watches("trap"))
        }

    @Test
    fun `the warning is posted once and reported`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()
            honeypot.refresh()

            assertEquals(listOf("trap"), guild.posted)
            assertEquals(HoneypotWarning("trap", "warning-1"), backend.state.warning)
            assertEquals("Nicht in diesen Kanal schreiben! / Do not write in this channel!", guild.embeds.single().title)
        }

    @Test
    fun `a warning that is gone is posted again`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            guild.missing = true
            honeypot.warningDeleted("warning-1")
            honeypot.refresh()

            assertEquals(listOf("trap", "trap"), guild.posted)
            assertEquals(HoneypotWarning("trap", "warning-2"), backend.state.warning)
        }

    @Test
    fun `a warning that exists after a restart is not posted again, but brought up to date once`() =
        runTest {
            backend.state = backend.state.copy(warning = HoneypotWarning("trap", "old"))
            val honeypot = honeypot()

            honeypot.refresh()
            honeypot.refresh()

            assertEquals(emptyList(), guild.posted)
            assertEquals(listOf(Honeypot.warningEmbed()), guild.updated)
        }

    @Test
    fun `the warning speaks German and English`() {
        val warning = Honeypot.warningEmbed()

        assertContains(warning.title.orEmpty(), "Nicht in diesen Kanal schreiben!")
        assertContains(warning.title.orEmpty(), "Do not write in this channel!")
        assertContains(warning.description.orEmpty(), "automatisch vom Server gekickt")
        assertContains(warning.description.orEmpty(), "automatically kicked from the server")
    }

    @Test
    fun `the direct message of a kick comes in the language of the member`() =
        runTest {
            memberLanguage = Language.ENGLISH
            val honeypot = honeypot()
            honeypot.refresh()

            honeypot.handle(message())

            assertEquals(listOf(Honeypot.kickMessage("WarEra", Language.ENGLISH)), directMessageTexts)
            assertEquals(listOf("1"), guild.kicked)
        }

    @Test
    fun `the warning moves with the honeypot and goes when it is removed`() =
        runTest {
            val honeypot = honeypot()
            honeypot.refresh()

            backend.state = backend.state.copy(channelId = "other")
            honeypot.refresh()
            assertEquals(listOf("warning-1"), guild.deleted)
            assertEquals(HoneypotWarning("other", "warning-2"), backend.state.warning)

            backend.state = backend.state.copy(channelId = null)
            honeypot.refresh()
            assertEquals(listOf("warning-1", "warning-2"), guild.deleted)
            assertEquals(null, backend.state.warning)
        }

    @Test
    fun `a warning the backend did not take is not posted twice`() =
        runTest {
            val failing =
                object : HoneypotBackend by backend {
                    var fail = true

                    override suspend fun reportHoneypotWarning(report: HoneypotWarningReport) {
                        if (fail) {
                            fail = false
                            throw IOException("backend is down")
                        }
                        backend.reportHoneypotWarning(report)
                    }
                }
            val flaky = Honeypot(failing, guild, { _, _ -> }, { Language.GERMAN }, { "WarEra" }, { _, call -> launch { call() } }, { time })
            flaky.refresh()
            flaky.refresh()

            assertEquals(listOf("trap"), guild.posted)
            assertEquals(HoneypotWarning("trap", "warning-1"), backend.state.warning)
        }

    @Test
    fun `a channel that is gone goes to the system log once`() =
        runTest {
            guild.channelGone = true
            val honeypot = honeypot()
            honeypot.refresh()
            honeypot.refresh()
            runCurrent()

            assertEquals(listOf(Honeypot.CHANNEL_GONE), backend.problems)
        }
}
