package eu.luftiger.bits.bot.verifypanel

import eu.luftiger.bits.bot.backend.VerifyPanelBackend
import eu.luftiger.bits.bot.commands.VerifyCommand
import eu.luftiger.bits.shared.VerifyPanelMessage
import eu.luftiger.bits.shared.VerifyPanelProblemReport
import eu.luftiger.bits.shared.VerifyPanelReport
import eu.luftiger.bits.shared.VerifyPanelState
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyPanelTest {
    private class FakeBackend : VerifyPanelBackend {
        var state = VerifyPanelState(channelId = "start")
        var down = false
        val problems = mutableListOf<String>()

        override suspend fun verifyPanel(): VerifyPanelState {
            if (down) throw IOException("backend is down")
            return state
        }

        override suspend fun reportVerifyPanelMessage(report: VerifyPanelReport) {
            if (down) throw IOException("backend is down")
            state = state.copy(message = report.message)
        }

        override suspend fun reportVerifyPanelProblem(report: VerifyPanelProblemReport) {
            problems += report.reason
        }
    }

    private class FakeGuild : VerifyPanelGuild {
        val posted = mutableListOf<String>()
        val buttons = mutableListOf<Button>()
        val updated = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var missing = false
        var postFails: Exception? = null
        var deleteFails = false

        override suspend fun post(
            channelId: String,
            embed: MessageEmbed,
            button: Button,
        ): String {
            postFails?.let { throw it }
            posted += channelId
            buttons += button
            return "panel-${posted.size}"
        }

        override suspend fun update(
            message: VerifyPanelMessage,
            embed: MessageEmbed,
            button: Button,
        ): Boolean {
            if (missing) return false
            updated += message.messageId
            return true
        }

        override suspend fun delete(message: VerifyPanelMessage) {
            if (deleteFails) throw IOException("refused")
            deleted += message.messageId
        }
    }

    private val backend = FakeBackend()
    private val guild = FakeGuild()

    private fun CoroutineScope.panel() = VerifyPanel(backend, guild) { _, call -> launch { call() } }

    @Test
    fun `posts the message with the button once and reports it`() =
        runTest {
            val panel = panel()

            panel.refresh()
            panel.refresh()

            assertEquals(listOf("start"), guild.posted)
            assertEquals(VerifyCommand.PANEL_BUTTON, guild.buttons.single().customId)
            assertEquals(VerifyPanelMessage("start", "panel-1"), backend.state.message)
        }

    @Test
    fun `a message posted before a restart is updated instead of posted again`() =
        runTest {
            backend.state = VerifyPanelState("start", VerifyPanelMessage("start", "old"))

            panel().refresh()

            assertEquals(emptyList(), guild.posted)
            assertEquals(listOf("old"), guild.updated)
        }

    @Test
    fun `a deleted message is posted again`() =
        runTest {
            val panel = panel()
            panel.refresh()

            panel.messageDeleted("panel-1")
            guild.missing = true
            panel.refresh()

            assertEquals(listOf("start", "start"), guild.posted)
            assertEquals(VerifyPanelMessage("start", "panel-2"), backend.state.message)
        }

    @Test
    fun `a new channel replaces the message instead of adding a second one`() =
        runTest {
            val panel = panel()
            panel.refresh()

            backend.state = backend.state.copy(channelId = "welcome")
            panel.refresh()

            assertEquals(listOf("panel-1"), guild.deleted)
            assertEquals(listOf("start", "welcome"), guild.posted)
            assertEquals(VerifyPanelMessage("welcome", "panel-2"), backend.state.message)
        }

    @Test
    fun `removing the channel removes the message`() =
        runTest {
            val panel = panel()
            panel.refresh()

            backend.state = backend.state.copy(channelId = null)
            panel.refresh()

            assertEquals(listOf("panel-1"), guild.deleted)
            assertEquals(VerifyPanelState(), backend.state)
        }

    @Test
    fun `a message that cannot be deleted does not keep the new channel empty`() =
        runTest {
            val panel = panel()
            panel.refresh()
            guild.deleteFails = true

            backend.state = backend.state.copy(channelId = "welcome")
            panel.refresh()
            runCurrent()

            assertEquals(listOf("start", "welcome"), guild.posted)
            assertEquals(listOf(VerifyPanel.REFUSED), backend.problems)
        }

    @Test
    fun `a problem is reported once, not with every poll`() =
        runTest {
            guild.postFails = InsufficientPermissionException(mockk<Guild>(relaxed = true), Permission.MESSAGE_SEND)
            val panel = panel()

            panel.refresh()
            panel.refresh()
            runCurrent()

            assertEquals(listOf(VerifyPanel.NO_PERMISSION), backend.problems)
        }

    @Test
    fun `a message the backend has not taken yet is reported again`() =
        runTest {
            val panel = panel()
            backend.down = true
            panel.refresh()
            assertEquals(emptyList(), guild.posted)

            backend.down = false
            panel.refresh()

            assertEquals(listOf("start"), guild.posted)
            assertEquals(VerifyPanelMessage("start", "panel-1"), backend.state.message)
        }

    @Test
    fun `the message and the button are in both languages`() {
        val description = VerifyPanel.embed().description.orEmpty()

        assertEquals(true, description.contains("Jetzt verifizieren") && description.contains("Verify now"))
        assertEquals("Jetzt verifizieren / Verify now", VerifyPanel.button().label)
    }
}
