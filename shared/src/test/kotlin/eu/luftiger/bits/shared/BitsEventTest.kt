package eu.luftiger.bits.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitsEventTest {
    @Test
    fun `event survives an encode-decode round trip`() {
        val event = PingEvent(discordUserId = "123", occurredAt = 1_700_000_000_000)

        assertEquals(event, decodeEvent(event.encode()))
    }

    @Test
    fun `encoded event carries its type`() {
        val payload = PingEvent(discordUserId = "123", occurredAt = 0).encode()

        assertTrue(payload.contains("\"type\":\"ping\""), payload)
    }
}
