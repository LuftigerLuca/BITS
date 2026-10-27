package eu.luftiger.bits.backend.feature.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.feature.api.BitsFeature
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Which features are on; everybody logged in may ask, since the dashboard hides the pages of those that are off. */
@RestController
@RequestMapping("/web/features")
class FeatureController(
    private val featureService: FeatureService,
) {
    @GetMapping
    fun features(): List<FeatureState> = featureService.all().map { (feature, enabled) -> FeatureState(feature, enabled) }
}

/** Switches a feature on or off; only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/features")
class FeatureAdminController(
    private val featureService: FeatureService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PutMapping("/{feature}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun set(
        @PathVariable feature: BitsFeature,
        @RequestBody request: SetFeatureRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        featureService.set(feature, request.enabled, actor)
        log.info("Feature {} switched {} by {}", feature, if (request.enabled) "on" else "off", session.subject)
    }
}

@Serializable
data class FeatureState(
    val feature: BitsFeature,
    val enabled: Boolean,
)

@Serializable
data class SetFeatureRequest(
    val enabled: Boolean,
)
