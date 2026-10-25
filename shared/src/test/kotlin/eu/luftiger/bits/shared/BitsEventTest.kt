package eu.luftiger.bits.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
                nicknames = mapOf("1" to "Rommel"),
                nicknameResets = mapOf("2" to "Guderian"),
            )

        assertEquals(event, decodeEvent(event.encode()))
    }

    @Test
    fun `role sync of a backend without nicknames leaves them alone`() {
        val payload = """{"type":"role_sync","managedRoleIds":[],"discordUserIds":null,"members":{},"occurredAt":5}"""

        val event = decodeEvent(payload) as RoleSyncEvent

        assertEquals(null, event.nicknames)
        assertEquals(emptyMap(), event.nicknameResets)
    }

    @Test
    fun `fields added by a newer sender are ignored`() {
        val payload = """{"type":"ping","discordUserId":"123","occurredAt":5,"addedLater":{"any":"thing"}}"""

        assertEquals(PingEvent(discordUserId = "123", occurredAt = 5), decodeEvent(payload))
    }

    @Test
    fun `an unknown event type is rejected, not mistaken for a known one`() {
        assertFailsWith<IllegalArgumentException> { decodeEvent("""{"type":"added_later","occurredAt":5}""") }
    }
}
