package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.DiscordRole
import org.springframework.data.jpa.repository.JpaRepository

interface DiscordRoleRepository : JpaRepository<DiscordRole, String>
