package eu.luftiger.bits.backend.rolesync.api

import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the role sync for changes outside the rules that change who gets a managed role. */
@NamedInterface("api")
interface RoleSyncRequests {
    /**
     * Syncs all members in the background with the WarEra data of the last sync, like after a rule change. Call it after
     * the change was committed. [confirmedRoleIds] are the roles the admin changed, which the bot may then take from as
     * many members as it takes, see [eu.luftiger.bits.shared.RoleSyncEvent.confirmedRoleIds].
     *
     * [revokedRoleIds] are roles BITS no longer manages after the change, which this sync takes from everybody once,
     * unless a rule or DSK still gives them; they count as confirmed as well. While the role sync is switched off, they
     * are dropped and the members keep them.
     */
    fun syncAfterChange(
        confirmedRoleIds: Collection<String>,
        revokedRoleIds: Collection<String> = emptyList(),
    )

    /**
     * Syncs [discordUserId] alone in the background with fresh WarEra data, as an admin asked for; a member without
     * link loses the managed roles. Returns `false` if nothing is synced, since the role sync, nicknames and status
     * roles are all switched off.
     */
    fun syncMember(discordUserId: String): Boolean
}
