package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.NicknameSetting
import org.springframework.data.jpa.repository.JpaRepository

interface NicknameSettingRepository : JpaRepository<NicknameSetting, Short>
