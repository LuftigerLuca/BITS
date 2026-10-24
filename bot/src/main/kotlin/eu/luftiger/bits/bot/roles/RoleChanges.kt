package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.RoleSyncEvent

data class RoleChange(
    val discordUserId: String,
    val roleId: String,
    val add: Boolean,
    val reason: String,
)

/** Changes that bring the managed roles of one member, who has [currentRoleIds] now, to the target of [event]. */
fun roleChanges(
    discordUserId: String,
    currentRoleIds: Set<String>,
    event: RoleSyncEvent,
): List<RoleChange> {
    val target = event.members[discordUserId]
    val grants = target?.grants.orEmpty().associateBy { it.roleId }
    val keep = target?.keepRoleIds.orEmpty().toSet()
    return event.managedRoleIds.mapNotNull { roleId ->
        val has = roleId in currentRoleIds
        val grant = grants[roleId]
        when {
            grant != null && !has -> RoleChange(discordUserId, roleId, add = true, reason = grant.reason)
            grant == null && has && roleId !in keep -> RoleChange(discordUserId, roleId, add = false, reason = REMOVE_REASON)
            else -> null
        }
    }
}

const val REMOVE_REASON = "BITS: keine passende Regel"
