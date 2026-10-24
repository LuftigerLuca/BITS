package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.RoleSyncEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppliedSyncsTest {
    private val applied = AppliedSyncs()

    @Test
    fun `without a remembered sync every sync applies, as after a restart`() {
        assertFalse(applied.isStale("1", fullSync(at = 0)))
        assertFalse(applied.isStale("1", memberSync("1", at = 0)))
    }

    @Test
    fun `an older full sync does not undo the sync of a member who relinked`() {
        applied.record(memberSync("1", at = 20))

        assertTrue(applied.isStale("1", fullSync(at = 10)))
        assertFalse(applied.isStale("2", fullSync(at = 10)))
        assertFalse(applied.isStale("1", fullSync(at = 20)))
    }

    @Test
    fun `an older member sync does not undo a newer full sync`() {
        applied.record(fullSync(at = 20))

        assertTrue(applied.isStale("1", memberSync("1", at = 10)))
        assertTrue(applied.isStale("1", fullSync(at = 10)))
        assertFalse(applied.isStale("1", memberSync("1", at = 30)))
    }

    @Test
    fun `a late sync does not move the remembered time back`() {
        applied.record(memberSync("1", at = 20))
        applied.record(memberSync("1", at = 10))
        applied.record(fullSync(at = 30))
        applied.record(fullSync(at = 15))

        assertTrue(applied.isStale("1", memberSync("1", at = 25)))
    }

    private fun fullSync(at: Long) = RoleSyncEvent(listOf("r"), discordUserIds = null, members = emptyMap(), occurredAt = at)

    private fun memberSync(
        discordUserId: String,
        at: Long,
    ) = RoleSyncEvent(listOf("r"), discordUserIds = listOf(discordUserId), members = emptyMap(), occurredAt = at)
}
