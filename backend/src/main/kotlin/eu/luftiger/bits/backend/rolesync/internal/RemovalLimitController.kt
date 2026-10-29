package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The removal limit in the dashboard. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/role-sync/removal-limit")
class RemovalLimitController(
    private val removalLimitService: RemovalLimitService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun removalLimit(): RemovalLimitState = RemovalLimitState(removalLimitService.isEnabled())

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun set(
        @RequestBody request: RemovalLimitState,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        removalLimitService.set(request.enabled, actor)
        log.info("Removal limit switched {} by {}", if (request.enabled) "on" else "off", session.subject)
    }
}

@Serializable
data class RemovalLimitState(
    val enabled: Boolean,
)
