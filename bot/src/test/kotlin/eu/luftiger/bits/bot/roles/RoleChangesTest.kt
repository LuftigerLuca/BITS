package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleGrant
import eu.luftiger.bits.shared.RoleSyncEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class RoleChangesTest {
    private val event =
        RoleSyncEvent(
            managedRoleIds = listOf("citizen", "minister", "veteran"),
            discordUserIds = null,
            members =
                mapOf(
                    "1" to MemberRoles(grants = listOf(RoleGrant("citizen", "BITS: Staatsbürgerschaft Germany"))),
                    "2" to MemberRoles(grants = emptyList(), keepRoleIds = listOf("citizen", "minister", "veteran")),
                ),
            occurredAt = 0,
        )

    @Test
    fun `grants missing roles and takes managed roles without a rule`() {
        assertEquals(
            listOf(
                RoleChange("1", "citizen", add = true, reason = "BITS: Staatsbürgerschaft Germany"),
                RoleChange("1", "veteran", add = false, reason = REMOVE_REASON),
            ),
            roleChanges("1", setOf("veteran", "unmanaged"), event),
        )
    }

    @Test
    fun `roles the member has already are left alone`() {
        assertEquals(emptyList(), roleChanges("1", setOf("citizen"), event))
    }

    @Test
    fun `member without target loses all managed roles, but nothing else`() {
        assertEquals(
            listOf("citizen", "minister"),
            roleChanges("3", setOf("citizen", "minister", "unmanaged"), event).map { it.roleId },
        )
    }

    @Test
    fun `roles to keep are neither given nor taken`() {
        assertEquals(emptyList(), roleChanges("2", setOf("minister"), event))
    }

    private val limit = RemovalLimit(minRemovals = 10, maxShare = 0.25)

    private fun removals(
        roleId: String,
        count: Int,
    ) = (1..count).map { RoleChange("$it", roleId, add = false, reason = REMOVE_REASON) }

    @Test
    fun `a sync of all members that takes a role from many of its holders is over the limit`() {
        val changes = removals("citizen", 30) + removals("veteran", 9) + removals("minister", 10)
        val holders = mapOf("citizen" to 100, "veteran" to 9, "minister" to 100)

        assertEquals(mapOf("citizen" to 30), rolesOverRemovalLimit(changes, holders, event, limit))
    }

    @Test
    fun `grants, member syncs and confirmed roles are never limited`() {
        val changes = removals("citizen", 30) + (1..30).map { RoleChange("$it", "veteran", add = true, reason = "BITS") }
        val holders = mapOf("citizen" to 30)

        assertEquals(emptyMap(), rolesOverRemovalLimit(changes, holders, event.copy(discordUserIds = listOf("1")), limit))
        assertEquals(emptyMap(), rolesOverRemovalLimit(changes, holders, event.copy(confirmedRoleIds = listOf("citizen")), limit))
    }

    @Test
    fun `nothing is limited while an admin switched the limit off`() {
        val changes = removals("citizen", 30)
        val holders = mapOf("citizen" to 30)

        assertEquals(emptyMap(), rolesOverRemovalLimit(changes, holders, event.copy(removalLimit = false), limit))
    }
}
