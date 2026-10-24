package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.DiscordRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DiscordRoleRepository : JpaRepository<DiscordRole, String> {
    /** Only the IDs: loading the roles themselves would clash with replacing them in the same transaction. */
    @Query("SELECT r.id FROM DiscordRole r WHERE r.assignable = true")
    fun findAssignableIds(): List<String>
}
