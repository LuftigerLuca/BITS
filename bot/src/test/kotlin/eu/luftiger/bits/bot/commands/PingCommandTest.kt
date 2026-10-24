package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.PingResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PingCommandTest {
    private val backend = mockk<BackendClient>()
    private val replies = Replies()

    @Test
    fun `shows the answer of the backend`() =
        runTest {
            coEvery { backend.ping("42") } returns PingResponse(pingCount = 3, wareraCountryCount = null)

            PingCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand("ping", replies))
            runCurrent()

            assertEquals(listOf("Pong! Pings in den letzten 30 Tagen: 3, WarEra: nicht erreichbar"), replies.messages)
        }

    @Test
    fun `says BITS is not reachable when the backend fails`() =
        runTest {
            coEvery { backend.ping(any()) } throws IllegalStateException("connection refused")

            PingCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand("ping", replies))
            runCurrent()

            assertEquals(listOf(BITS_UNAVAILABLE_MESSAGE), replies.messages)
        }

    @Test
    fun `other commands are left to their own listener`() =
        runTest {
            PingCommand(backend, backgroundScope).onSlashCommandInteraction(slashCommand("verify", replies))
            runCurrent()

            assertEquals(emptyList(), replies.messages)
        }
}
