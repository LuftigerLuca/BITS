package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.WareraUnavailableException
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.Language
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
    @Test
    fun `a new link asks for the language with its buttons, a failed check does not`() =
        runTest {
            val backend = mockk<BackendClient>()
            val command = VerifyCommand(backend, backgroundScope, languages())
            val linked = Replies()
            coEvery { backend.checkVerification("42", "ABC", any()) } returns CheckVerificationResponse.Linked("Luftiger")
            command.onButtonInteraction(buttonClick(VerifyCommand.CHECK_BUTTON_PREFIX + "ABC", linked))
            runCurrent()

            assertContains(linked.messages.single(), "In welcher Sprache soll BITS dir schreiben")
            assertContains(linked.components.toString(), LanguageCommand.GERMAN_BUTTON)
            assertContains(linked.components.toString(), LanguageCommand.ENGLISH_BUTTON)

            val missing = Replies()
            coEvery { backend.checkVerification("42", "DEF", any()) } returns CheckVerificationResponse.CodeMissing
            command.onButtonInteraction(buttonClick(VerifyCommand.CHECK_BUTTON_PREFIX + "DEF", missing))
            runCurrent()

            assertEquals(emptyList(), missing.components.toList())
        }

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
        val message = VerifyCommand.startedMessage(started, Language.GERMAN)

        assertContains(message, "`BITS-ABC234`")
        assertContains(message, "<t:1800:R>")
        assertContains(message, "\\_Luftiger\\_")
        assertFalse(message.contains("bereits"))
    }

    @Test
    fun `started message warns against entering codes of others`() {
        assertTrue(VerifyCommand.startedMessage(started, Language.GERMAN).endsWith(VerifyCommand.socialEngineeringWarning(Language.GERMAN)))
    }

    @Test
    fun `started message says what BITS stores and how to delete it`() {
        assertContains(VerifyCommand.startedMessage(started, Language.GERMAN), VerifyCommand.privacyNotice(Language.GERMAN))
        assertContains(VerifyCommand.privacyNotice(Language.GERMAN), "`/unverify`")
    }

    @Test
    fun `started message warns about an existing link`() {
        val message = VerifyCommand.startedMessage(started.copy(linkedWareraUsername = "Old"), Language.GERMAN)

        assertTrue(message.startsWith("Hinweis: Dein Discord-Account ist bereits mit **Old** verknüpft."), message)
    }

    @Test
    fun `too early message names the seconds to wait`() {
        assertContains(VerifyCommand.checkMessage(CheckVerificationResponse.TooEarly(retryAfterSeconds = 7), Language.GERMAN), "7 Sekunden")
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
        messages.forEach { (response, expected) -> assertContains(VerifyCommand.checkMessage(response, Language.GERMAN), expected) }
        assertContains(VerifyCommand.checkMessage(CheckVerificationResponse.Linked("x"), Language.GERMAN), "Rollen")
    }

    @Test
    fun `verify shows the code with the button to check it`() =
        runTest {
            coEvery { backend.startVerification("42", "Luftiger") } returns started

            command().onSlashCommandInteraction(slashCommand("verify", replies, mapOf("name" to "Luftiger")))
            runCurrent()

            assertEquals(listOf(VerifyCommand.startedMessage(started, Language.GERMAN)), replies.messages)
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
            assertEquals(bitsUnavailableMessage(Language.GERMAN), replies.messages[1])
        }

    @Test
    fun `a check answers once its result arrives as event`() =
        runTest {
            coEvery { backend.checkVerification("42", "BITS-ABC234", "Luca") } returns CheckVerificationResponse.Checking
            val command = command()

            command.onButtonInteraction(buttonClick("verify:check:BITS-ABC234", replies))
            runCurrent()
            assertEquals(emptyList(), replies.messages)

            command.onChecked(VerificationCheckedEvent("42", "BITS-ABC234", CheckVerificationResponse.Linked("Luftiger"), occurredAt = 0))

            assertEquals(
                listOf(VerifyCommand.checkMessage(CheckVerificationResponse.Linked("Luftiger"), Language.GERMAN)),
                replies.messages,
            )
        }

    @Test
    fun `a check answered right away needs no event`() =
        runTest {
            coEvery { backend.checkVerification("42", "BITS-ABC234", "Luca") } returns CheckVerificationResponse.TooEarly(4)

            command().onButtonInteraction(buttonClick("verify:check:BITS-ABC234", replies))
            runCurrent()

            assertEquals(listOf(VerifyCommand.checkMessage(CheckVerificationResponse.TooEarly(4), Language.GERMAN)), replies.messages)
        }

    @Test
    fun `buttons of other features are ignored`() =
        runTest {
            command().onButtonInteraction(buttonClick("other:thing", replies))
            runCurrent()

            assertEquals(emptyList(), replies.messages)
        }

    private fun TestScope.command() = VerifyCommand(backend, backgroundScope, languages())
}
