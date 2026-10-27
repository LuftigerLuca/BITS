package eu.luftiger.bits.backend.feature.internal

import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.domain.FeatureSetting
import org.springframework.data.jpa.repository.JpaRepository

interface FeatureSettingRepository : JpaRepository<FeatureSetting, BitsFeature>
