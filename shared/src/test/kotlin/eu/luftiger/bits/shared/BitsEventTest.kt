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

    @Test
    fun `role sync survives a round trip, including a sync of all members`() {
        val event =
            RoleSyncEvent(
                managedRoleIds = listOf("r1", "r2"),
                discordUserIds = null,
                members = mapOf("1" to MemberRoles(grants = listOf(RoleGrant("r1", "BITS: Level ab 10")), keepRoleIds = listOf("r2"))),
                occurredAt = 5,
            )

        assertEquals(event, decodeEvent(event.encode()))
    }
}
