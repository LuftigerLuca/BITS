package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Whether BITS sets nicknames to WarEra names. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/role-sync/nicknames")
class NicknameSettingController(
    private val settingService: NicknameSettingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun setting(): NicknameSettingResponse = NicknameSettingResponse(settingService.enabled())

    @PutMapping
    fun setSetting(
        @RequestBody request: NicknameSettingRequest,
        @AuthenticationPrincipal session: Jwt,
    ): NicknameSettingResponse {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        settingService.setEnabled(request.enabled, actor)
        log.info("Nicknames from WarEra names {} by {}", if (request.enabled) "enabled" else "disabled", session.subject)
        return NicknameSettingResponse(request.enabled)
    }
}

@Serializable
data class NicknameSettingRequest(
    val enabled: Boolean,
)

@Serializable
data class NicknameSettingResponse(
    val enabled: Boolean,
)
