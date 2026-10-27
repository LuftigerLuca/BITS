package eu.luftiger.bits.backend.feature.api

import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the feature module: whether an admin switched a feature off. */
@NamedInterface("api")
interface FeatureToggles {
    /** A feature is on until an admin switches it off. */
    fun isEnabled(feature: BitsFeature): Boolean
}
