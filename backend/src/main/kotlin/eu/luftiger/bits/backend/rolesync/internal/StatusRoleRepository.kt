package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.StatusRoleSetting
import org.springframework.data.jpa.repository.JpaRepository

interface StatusRoleRepository : JpaRepository<StatusRoleSetting, Short>
