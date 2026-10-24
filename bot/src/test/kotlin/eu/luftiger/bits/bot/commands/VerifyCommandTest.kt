package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.StartVerificationResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyCommandTest {
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
    fun `started message warns about an existing link`() {
        val message = VerifyCommand.startedMessage(started.copy(linkedWareraUsername = "Old"))

        assertTrue(message.startsWith("Hinweis: Dein Discord-Account ist bereits mit **Old** verknüpft."), message)
    }

    @Test
    fun `too early message names the seconds to wait`() {
        assertContains(VerifyCommand.checkMessage(CheckVerificationResponse.TooEarly(retryAfterSeconds = 7)), "7 Sekunden")
    }

    @Test
    fun `results of the background check have their own messages`() {
        assertContains(VerifyCommand.checkMessage(CheckVerificationResponse.Linked("Luftiger")), "Fertig!")
        assertContains(VerifyCommand.checkMessage(CheckVerificationResponse.WareraUnavailable), "WarEra ist gerade nicht erreichbar")
    }
}
