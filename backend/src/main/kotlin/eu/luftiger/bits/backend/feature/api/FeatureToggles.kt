package eu.luftiger.bits.backend.feature.api

import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the feature module: whether an admin switched a feature off. */
@NamedInterface("api")
interface FeatureToggles {
    /** Without a switch by an admin, a feature is as [BitsFeature.enabledByDefault] says. */
    fun isEnabled(feature: BitsFeature): Boolean
}
