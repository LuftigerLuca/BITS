package eu.luftiger.bits.backend.feature.internal

import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.shared.FeatureStates
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** The bot asks every 30 seconds and answers the commands of a feature that is off with a hint instead. */
@RestController
class FeatureBotController(
    private val featureService: FeatureService,
) {
    @GetMapping("/api/features")
    fun features(): FeatureStates {
        val features = featureService.all()
        return FeatureStates(
            verification = features.getValue(BitsFeature.VERIFICATION),
            roleSync = features.getValue(BitsFeature.ROLE_SYNC),
            nicknames = features.getValue(BitsFeature.NICKNAMES),
            profile = features.getValue(BitsFeature.PROFILE),
            votes = features.getValue(BitsFeature.VOTES),
        )
    }
}
