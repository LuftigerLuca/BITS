package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.api.MemberCountries
import org.springframework.stereotype.Component

@Component
class MemberCountryLookup(
    private val syncService: RoleSyncService,
) : MemberCountries {
    override fun byWareraUserId(): Map<String, String> =
        syncService
            .lastLoadedUsers()
            .mapNotNull { (id, loaded) -> loaded.user?.country?.let { id to it } }
            .toMap()
}
