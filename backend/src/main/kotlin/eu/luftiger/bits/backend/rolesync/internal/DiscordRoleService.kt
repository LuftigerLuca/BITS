package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.DiscordRole
import eu.luftiger.bits.shared.DiscordRoleDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiscordRoleService(
    private val roleRepository: DiscordRoleRepository,
) {
    /** Rules of roles missing from [roles] become orphaned; they are kept, but no longer run. */
    @Transactional
    fun replaceAll(roles: List<DiscordRoleDto>) {
        roleRepository.deleteAllInBatch()
        roleRepository.saveAll(roles.map { DiscordRole(it.id, it.name, it.color, it.position, it.assignable) })
    }
}
