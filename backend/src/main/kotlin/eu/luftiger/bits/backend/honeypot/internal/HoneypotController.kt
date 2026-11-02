package eu.luftiger.bits.backend.honeypot.internal

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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The honeypot in the dashboard; only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/honeypot")
class HoneypotController(
    private val service: HoneypotService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun honeypot(): HoneypotView {
        val state = service.state()
        return HoneypotView(
            channelId = state.channelId,
            kicksPaused = state.kicksPaused,
            channels = service.channels().map { HoneypotChannelOption(it.id, it.name, it.category, it.honeypotUsable) },
        )
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setChannel(
        @Valid @RequestBody request: HoneypotChannelRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        service.setChannel(request.channelId, actor(session))
        log.info("Honeypot channel set to {} by {}", request.channelId, session.subject)
    }

    @PostMapping("/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resume(
        @AuthenticationPrincipal session: Jwt,
    ) {
        service.resumeKicks(actor(session))
        log.info("Honeypot kicks resumed by {}", session.subject)
    }

    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
}

@Serializable
data class HoneypotView(
    /** `null` while no honeypot is set. */
    val channelId: String?,
    val kicksPaused: Boolean,
    /** Text channels in the order of Discord. */
    val channels: List<HoneypotChannelOption>,
)

@Serializable
data class HoneypotChannelOption(
    val id: String,
    val name: String,
    /** `null` for none. */
    val category: String?,
    /** Whether the bot has what a honeypot takes there. */
    val usable: Boolean,
)

@Serializable
data class HoneypotChannelRequest(
    /** `null` to remove the honeypot. */
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val channelId: String?,
)
