package eu.luftiger.bits.backend.feature.domain

import eu.luftiger.bits.backend.feature.api.BitsFeature
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Whether a feature is on; written once an admin switches it for the first time, until then it is on. */
@Entity
@Table(name = "feature_setting")
class FeatureSetting(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "feature")
    val feature: BitsFeature,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)
