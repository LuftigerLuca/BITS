package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.RoleSyncEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class NicknameChangesTest {
    @Test
    fun `cuts WarEra names off at Discord's limit`() {
        val long = "x".repeat(40)

        assertEquals(NicknameChange("1", "x".repeat(32)), nicknameChange("1", null, event(nicknames = mapOf("1" to long))))
        assertEquals(null, nicknameChange("1", "x".repeat(32), event(nicknames = mapOf("1" to long))))
    }

    @Test
    fun `a linked member's name wins over a reset`() {
        val event = event(nicknames = mapOf("1" to "Patton"), resets = mapOf("1" to "Rommel"))

        assertEquals(NicknameChange("1", "Patton"), nicknameChange("1", "Rommel", event))
    }

    private fun event(
        nicknames: Map<String, String>?,
        resets: Map<String, String> = emptyMap(),
    ) = RoleSyncEvent(emptyList(), null, emptyMap(), 0, nicknames = nicknames, nicknameResets = resets)
}
