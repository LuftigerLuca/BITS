package eu.luftiger.bits.backend.verifypanel.internal

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

/** The channel with the button to verify in the dashboard; only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/verify-panel")
class VerifyPanelController(
    private val service: VerifyPanelService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun verifyPanel(): VerifyPanelView =
        VerifyPanelView(
            channelId = service.state().channelId,
            channels = service.channels().map { VerifyPanelChannelOption(it.id, it.name, it.category, it.postable) },
        )

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setChannel(
        @Valid @RequestBody request: VerifyPanelChannelRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val principal = SessionPrincipal.of(session)
        service.setChannel(request.channelId, AuditActor(principal.subject, principal.name, principal.avatarUrl))
        log.info("Verify panel channel set to {} by {}", request.channelId, session.subject)
    }
}

@Serializable
data class VerifyPanelView(
    /** `null` while no channel is set. */
    val channelId: String?,
    /** Text channels in the order of Discord. */
    val channels: List<VerifyPanelChannelOption>,
)

@Serializable
data class VerifyPanelChannelOption(
    val id: String,
    val name: String,
    /** `null` for none. */
    val category: String?,
    /** Whether the bot may post its message there. */
    val usable: Boolean,
)

@Serializable
data class VerifyPanelChannelRequest(
    /** `null` to remove the message. */
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val channelId: String?,
)
