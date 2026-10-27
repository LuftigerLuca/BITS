package eu.luftiger.bits.backend.feature.api

import org.springframework.modulith.NamedInterface

/** Application event, published within the transaction in which an admin switches a feature on or off. */
@NamedInterface("api")
data class FeatureToggled(
    val feature: BitsFeature,
    val enabled: Boolean,
)
