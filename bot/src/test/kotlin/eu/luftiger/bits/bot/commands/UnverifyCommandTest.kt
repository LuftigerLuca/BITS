package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.UnlinkAccountResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class UnverifyCommandTest {
    @Test
    fun `message names the former link and the roles that go`() {
        val message = UnverifyCommand.unlinkedMessage(UnlinkAccountResponse(wareraUsername = "_Luftiger_"))

        assertContains(message, "**\\_Luftiger\\_** ist gelöscht")
        assertContains(message, "Rollen")
    }

    @Test
    fun `command unlinks the user at the backend`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.unlinkAccount("42") } returns UnlinkAccountResponse(wareraUsername = "Luftiger")
            val replies = Replies()

            UnverifyCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand("unverify", replies))
            runCurrent()

            assertEquals(listOf(UnverifyCommand.unlinkedMessage(UnlinkAccountResponse("Luftiger"))), replies.messages)
        }

    @Test
    fun `command says BITS is not reachable when the backend fails`() =
        runTest {
            val backend = mockk<BackendClient>()
            coEvery { backend.unlinkAccount(any()) } throws IllegalStateException("connection refused")
            val replies = Replies()

            UnverifyCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand("unverify", replies))
            runCurrent()

            assertEquals(listOf(BITS_UNAVAILABLE_MESSAGE), replies.messages)
        }

    @Test
    fun `message says when there was no link`() {
        assertEquals(
            "Dein Discord-Account ist mit keinem WarEra-Account verknüpft.",
            UnverifyCommand.unlinkedMessage(UnlinkAccountResponse(wareraUsername = null)),
        )
    }
}
