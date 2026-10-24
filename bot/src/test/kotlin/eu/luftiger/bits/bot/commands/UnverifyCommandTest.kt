package eu.luftiger.bits.bot.commands

import eu.luftiger.bits.shared.UnlinkAccountResponse
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
    fun `message says when there was no link`() {
        assertEquals(
            "Dein Discord-Account ist mit keinem WarEra-Account verknüpft.",
            UnverifyCommand.unlinkedMessage(UnlinkAccountResponse(wareraUsername = null)),
        )
    }
}
