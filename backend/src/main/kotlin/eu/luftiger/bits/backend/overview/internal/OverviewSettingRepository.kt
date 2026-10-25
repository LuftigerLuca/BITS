package eu.luftiger.bits.backend.overview.internal

import eu.luftiger.bits.backend.overview.domain.OverviewSetting
import org.springframework.data.jpa.repository.JpaRepository

interface OverviewSettingRepository : JpaRepository<OverviewSetting, Short>
