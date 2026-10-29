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

/**
 * How many holders one sync of all members may take a role from. Members change citizenship or military unit one at
 * a time, so a real change from one hourly sync to the next takes a role from a few of its holders. A field WarEra
 * stops sending or renames takes it from all of them at once. A role is held back when the sync would take it from at
 * least [minRemovals] members and from more than [maxShare] of its holders; the small minimum keeps roles with few
 * holders, like an office, out of it.
 */
data class RemovalLimit(
    val minRemovals: Int = 10,
    val maxShare: Double = 0.25,
)

/**
 * Roles whose removals in [changes] exceed [limit], given how many members hold each role now ([holders]). Only syncs
 * of all members are limited, and never for the roles of [RoleSyncEvent.confirmedRoleIds].
 */
fun rolesOverRemovalLimit(
    changes: List<RoleChange>,
    holders: Map<String, Int>,
    event: RoleSyncEvent,
    limit: RemovalLimit,
): Map<String, Int> {
    if (event.discordUserIds != null) return emptyMap()
    return changes
        .filter { !it.add && it.roleId !in event.confirmedRoleIds }
        .groupingBy { it.roleId }
        .eachCount()
        .filter { (roleId, removals) -> removals >= limit.minRemovals && removals > limit.maxShare * (holders[roleId] ?: 0) }
}

/**
 * Changes that give a member, who has [currentRoleIds] now, the status role of [RoleSyncEvent.statusRoles] that fits
 * and take the other one; none if the event leaves the status roles alone.
 */
fun statusRoleChanges(
    discordUserId: String,
    currentRoleIds: Set<String>,
    event: RoleSyncEvent,
): List<RoleChange> {
    val status = event.statusRoles ?: return emptyList()
    val verified = discordUserId in status.verifiedDiscordUserIds
    val reason = if (verified) VERIFIED_REASON else UNVERIFIED_REASON
    val (granted, taken) =
        if (verified) {
            status.verifiedRoleId to status.unverifiedRoleId
        } else {
            status.unverifiedRoleId to
                status.verifiedRoleId
        }
    return listOfNotNull(
        granted?.takeIf { it !in currentRoleIds }?.let { RoleChange(discordUserId, it, add = true, reason = reason) },
        taken?.takeIf { it in currentRoleIds }?.let { RoleChange(discordUserId, it, add = false, reason = reason) },
    )
}

const val VERIFIED_REASON = "BITS: verifiziert"
const val UNVERIFIED_REASON = "BITS: nicht verifiziert"
