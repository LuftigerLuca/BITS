package eu.luftiger.bits.backend.presence.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.shared.BotPresence
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Status and activity of the bot in the dashboard; only for admins, see the security config of `/web/admin/`. Uses the
 * DTO the bot reads too, since both sides mean the same thing by it.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/bot-presence")
class BotPresenceController(
    private val presenceService: BotPresenceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun presence(): BotPresence = presenceService.presence()

    @PutMapping
    fun set(
        @RequestBody request: BotPresence,
        @AuthenticationPrincipal session: Jwt,
    ): BotPresence {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        val presence = presenceService.set(request, actor)
        log.info("Bot presence set to {} by {}", presence.status, session.subject)
        return presence
    }
}
