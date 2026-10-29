package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.api.RoleSyncRequests
import org.springframework.stereotype.Component

@Component
class RoleSyncRequestHandler(
    private val syncTrigger: RoleSyncTrigger,
) : RoleSyncRequests {
    override fun syncAfterChange(
        confirmedRoleIds: Collection<String>,
        revokedRoleIds: Collection<String>,
    ) = syncTrigger.syncAllWithCachedData(confirmedRoleIds + revokedRoleIds, revokedRoleIds)
}
