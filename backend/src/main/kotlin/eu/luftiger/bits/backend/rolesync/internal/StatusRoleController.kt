package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.shared.WebInput
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
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

/** The status roles in the dashboard. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/role-sync/status-roles")
class StatusRoleController(
    private val statusRoleService: StatusRoleService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun statusRoles(): StatusRolesResponse = statusRoleService.statusRoles()

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun set(
        @Valid @RequestBody request: StatusRolesRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        statusRoleService.set(request, actor)
        log.info("Status roles set to {} and {} by {}", request.verifiedRoleId, request.unverifiedRoleId, session.subject)
    }
}

@Serializable
data class StatusRolesRequest(
    /** `null` for none. */
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val verifiedRoleId: String? = null,
    /** `null` for none. */
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val unverifiedRoleId: String? = null,
)

@Serializable
data class StatusRolesResponse(
    /** `null` if none is set. */
    val verified: StatusRoleResponse?,
    /** `null` if none is set. */
    val unverified: StatusRoleResponse?,
)

@Serializable
data class StatusRoleResponse(
    val roleId: String,
    /** `null` if the role was deleted in Discord. */
    val roleName: String?,
    val roleColor: Int?,
    /** Like that of a rule: only an active role is given. */
    val status: RuleStatus,
)
