package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.RoleSyncSetting
import org.springframework.data.jpa.repository.JpaRepository

interface RoleSyncSettingRepository : JpaRepository<RoleSyncSetting, Short>
