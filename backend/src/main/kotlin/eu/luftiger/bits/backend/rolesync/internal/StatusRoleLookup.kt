package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggles
import eu.luftiger.bits.backend.rolesync.domain.StatusRoleSetting
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/** The roles for verified and unverified members as the role sync and `/rollen` use them. */
@Component
class StatusRoleLookup(
    private val repository: StatusRoleRepository,
    private val roleRepository: DiscordRoleRepository,
    private val features: FeatureToggles,
) {
    /** As an admin set them, whether the roles still exist or not; both `null` until then. */
    fun saved(): SavedStatusRoles =
        repository.findByIdOrNull(StatusRoleSetting.SINGLE_ROW_ID)?.let { SavedStatusRoles(it.verifiedRoleId, it.unverifiedRoleId) }
            ?: SavedStatusRoles(null, null)

    /**
     * The roles the bot gives, like the active rules only those it can give; `null` while the verification is switched
     * off or no such role is set, so that BITS leaves the status roles alone.
     */
    fun active(): SavedStatusRoles? {
        if (!features.isEnabled(BitsFeature.VERIFICATION)) return null
        val saved = saved()
        if (saved.verifiedRoleId == null && saved.unverifiedRoleId == null) return null
        val assignable = roleRepository.findAll().filter { it.assignable }.mapTo(HashSet()) { it.id }
        return SavedStatusRoles(saved.verifiedRoleId?.takeIf { it in assignable }, saved.unverifiedRoleId?.takeIf { it in assignable })
            .takeUnless { it.verifiedRoleId == null && it.unverifiedRoleId == null }
    }
}

data class SavedStatusRoles(
    val verifiedRoleId: String?,
    val unverifiedRoleId: String?,
) {
    val ids: Set<String> get() = setOfNotNull(verifiedRoleId, unverifiedRoleId)
}
