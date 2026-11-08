package eu.luftiger.bits.backend.verifypanel.internal

import eu.luftiger.bits.backend.verifypanel.domain.VerifyPanelSetting
import org.springframework.data.jpa.repository.JpaRepository

interface VerifyPanelRepository : JpaRepository<VerifyPanelSetting, Short>
