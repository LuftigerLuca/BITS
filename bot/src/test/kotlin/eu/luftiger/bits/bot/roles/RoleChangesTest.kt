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
}
