package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.VerificationCheckedEvent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyCommandTest {
    private val backend = mockk<BackendClient>()
    private val replies = Replies()
    private val started =
        StartVerificationResponse.Started(
            code = "BITS-ABC234",
            wareraUsername = "_Luftiger_",
            expiresAt = 1_800_000,
            linkedWareraUsername = null,
        )

    @Test
    fun `started message shows code and expiry and escapes the name`() {
        val message = VerifyCommand.startedMessage(started)

        assertContains(message, "`BITS-ABC234`")
        assertContains(message, "<t:1800:R>")
        assertContains(message, "\\_Luftiger\\_")
        assertFalse(message.contains("bereits"))
    }

    @Test
    fun `started message warns against entering codes of others`() {
        assertTrue(VerifyCommand.startedMessage(started).endsWith(VerifyCommand.SOCIAL_ENGINEERING_WARNING))
    }

    @Test
    fun `started message says what BITS stores and how to delete it`() {
        assertContains(VerifyCommand.startedMessage(started), VerifyCommand.PRIVACY_NOTICE)
        assertContains(VerifyCommand.PRIVACY_NOTICE, "`/unverify`")
    }

    @Test
    fun `started message warns about an existing link`() {
        val message = VerifyCommand.startedMessage(started.copy(linkedWareraUsername = "Old"))

        assertTrue(message.startsWith("Hinweis: Dein Discord-Account ist bereits mit **Old** verknüpft."), message)
    }

    @Test
    fun `too early message names the seconds to wait`() {
        assertContains(VerifyCommand.checkMessage(CheckVerificationResponse.TooEarly(retryAfterSeconds = 7)), "7 Sekunden")
    }

    @Test
    fun `every check result has its own message`() {
        val messages =
            mapOf(
                CheckVerificationResponse.Linked(
                    "_Luftiger_",
                ) to "Fertig! Dein Discord-Account ist jetzt mit **\\_Luftiger\\_** verknüpft.",
                CheckVerificationResponse.WareraUnavailable to "WarEra ist gerade nicht erreichbar",
                CheckVerificationResponse.CodeMissing to "Ich habe den Code in keinem deiner Firmennamen gefunden.",
                CheckVerificationResponse.Expired to "Der Code ist abgelaufen.",
                CheckVerificationResponse.Invalid to "Dieser Code ist nicht mehr gültig.",
                CheckVerificationResponse.TooEarly(3) to "Bitte warte noch 3 Sekunden",
            )
        messages.forEach { (response, expected) -> assertContains(VerifyCommand.checkMessage(response), expected) }
        assertContains(VerifyCommand.checkMessage(CheckVerificationResponse.Linked("x")), "Rollen")
    }

    @Test
    fun `verify shows the code with the button to check it`() =
        runTest {
            coEvery { backend.startVerification("42", "Luftiger") } returns started

            command().onSlashCommandInteraction(slashCommand("verify", replies, mapOf("name" to "Luftiger")))
            runCurrent()

            assertEquals(listOf(VerifyCommand.startedMessage(started)), replies.messages)
        }

    @Test
    fun `verify names an unknown user and a user without company`() =
        runTest {
            coEvery { backend.startVerification("42", "Nobody") } returns StartVerificationResponse.UserNotFound
            coEvery { backend.startVerification("42", "Poor") } returns StartVerificationResponse.NoCompany("Poor")

            command().onSlashCommandInteraction(slashCommand("verify", replies, mapOf("name" to "Nobody")))
            command().onSlashCommandInteraction(slashCommand("verify", replies, mapOf("name" to "Poor")))
            runCurrent()

            assertContains(replies.messages[0], "keinen WarEra-Account mit dem Namen **Nobody**")
            assertContains(replies.messages[1], "**Poor** hat keine Firma")
        }

    @Test
    fun `verify tells when WarEra or BITS cannot be reached`() =
        runTest {
            coEvery { backend.startVerification("42", "WarEra") } throws WareraUnavailableException(RuntimeException())
            coEvery { backend.startVerification("42", "BITS") } throws IllegalStateException("connection refused")

            command().onSlashCommandInteraction(slashCommand("verify", replies, mapOf("name" to "WarEra")))
            command().onSlashCommandInteraction(slashCommand("verify", replies, mapOf("name" to "BITS")))
            runCurrent()

            assertContains(replies.messages[0], "WarEra ist gerade nicht erreichbar")
            assertEquals(BITS_UNAVAILABLE_MESSAGE, replies.messages[1])
        }

    @Test
    fun `a check answers once its result arrives as event`() =
        runTest {
            coEvery { backend.checkVerification("42", "BITS-ABC234") } returns CheckVerificationResponse.Checking
            val command = command()

            command.onButtonInteraction(buttonClick("verify:check:BITS-ABC234", replies))
            runCurrent()
            assertEquals(emptyList(), replies.messages)

            command.onChecked(VerificationCheckedEvent("42", "BITS-ABC234", CheckVerificationResponse.Linked("Luftiger"), occurredAt = 0))

            assertEquals(listOf(VerifyCommand.checkMessage(CheckVerificationResponse.Linked("Luftiger"))), replies.messages)
        }

    @Test
    fun `a check answered right away needs no event`() =
        runTest {
            coEvery { backend.checkVerification("42", "BITS-ABC234") } returns CheckVerificationResponse.TooEarly(4)

            command().onButtonInteraction(buttonClick("verify:check:BITS-ABC234", replies))
            runCurrent()

            assertEquals(listOf(VerifyCommand.checkMessage(CheckVerificationResponse.TooEarly(4))), replies.messages)
        }

    @Test
    fun `buttons of other features are ignored`() =
        runTest {
            command().onButtonInteraction(buttonClick("other:thing", replies))
            runCurrent()

            assertEquals(emptyList(), replies.messages)
        }

    private fun TestScope.command() = VerifyCommand(backend, backgroundScope)
}
