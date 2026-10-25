package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.warera.WareraApiException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The embassy page of the dashboard. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/embassies")
class EmbassyController(
    private val embassyService: EmbassyService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun embassies(): EmbassiesResponse = embassyService.overview()

    /** Members of the guild to add by hand, by name or ID. */
    @GetMapping("/member-search")
    fun searchMembers(
        @RequestParam query: String,
    ): List<MemberOption> = embassyService.searchMembers(query)

    @PutMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setSettings(
        @RequestBody request: EmbassySettingsRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        embassyService.setSettings(request, actor(session))
        log.info("Embassy settings changed by {}", session.subject)
    }

    @PostMapping("/threads")
    @ResponseStatus(HttpStatus.CREATED)
    fun assign(
        @RequestBody request: AssignThreadRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        embassyService.assign(request.threadId, request.countryId, actor(session))
        log.info("Thread {} assigned to country {} by {}", request.threadId, request.countryId, session.subject)
    }

    @DeleteMapping("/threads/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassign(
        @PathVariable threadId: String,
        @AuthenticationPrincipal session: Jwt,
    ) {
        embassyService.unassign(threadId, actor(session))
        log.info("Embassy thread {} unassigned by {}", threadId, session.subject)
    }

    @PostMapping("/threads/{threadId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        @PathVariable threadId: String,
        @RequestBody request: AddMemberRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        embassyService.addMember(threadId, request.discordUserId, actor(session))
        log.info("Member {} added to embassy thread {} by {}", request.discordUserId, threadId, session.subject)
    }

    @DeleteMapping("/threads/{threadId}/members/{discordUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(
        @PathVariable threadId: String,
        @PathVariable discordUserId: String,
        @AuthenticationPrincipal session: Jwt,
    ) {
        embassyService.removeMember(threadId, discordUserId, actor(session))
        log.info("Member {} removed from embassy thread {} by {}", discordUserId, threadId, session.subject)
    }

    /** Only when assigning a thread, which checks the country. */
    @ExceptionHandler(WareraApiException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun wareraUnavailable(e: WareraApiException) {
        log.warn("WarEra API not reachable", e)
    }

    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
}

@Serializable
data class EmbassiesResponse(
    /** The country under "Verwaltung", which cannot get a thread; `null` while none is set. */
    val ownCountryId: String?,
    val settings: EmbassySettingsRequest,
    /** Highest first, for picking the ambassador and host roles. */
    val roles: List<RoleOption>,
    /** By country name. */
    val threads: List<EmbassyThreadResponse>,
    /** Private threads the bot reported that have no country yet. */
    val availableThreads: List<ThreadOption>,
)

@Serializable
data class EmbassySettingsRequest(
    /** The offices whose holders belong in the thread of their country. */
    val offices: List<GovernmentOffice>,
    /** `null` for none: the bot then gives and takes no role. */
    val ambassadorRoleId: String?,
    /** Roles whose members belong in every thread, without getting the ambassador role. */
    val hostRoleIds: List<String>,
)

@Serializable
data class RoleOption(
    val id: String,
    val name: String,
    val color: Int?,
)

@Serializable
data class EmbassyThreadResponse(
    val threadId: String,
    val threadName: String,
    /** `null` if the bot no longer reports the thread. */
    val channelName: String?,
    val countryId: String,
    val countryName: String,
    /** Whether the bot no longer reports the thread, e.g. because it was deleted. */
    val missing: Boolean,
    /** Who is in the thread and why, as of the last sync; empty until the bot reported one. */
    val members: List<ThreadMemberResponse>,
    /** Members an admin added by hand. */
    val manualMembers: List<MemberOption>,
)

@Serializable
data class ThreadMemberResponse(
    val discordUserId: String,
    val name: String,
    /** German, e.g. "Außenminister von Polen", "Manuell hinzugefügt" or "Gastgeber-Rolle @Regierung". */
    val reason: String,
)

@Serializable
data class MemberOption(
    val discordUserId: String,
    /** `null` if the bot has not reported the member. */
    val name: String?,
)

@Serializable
data class ThreadOption(
    val id: String,
    val name: String,
    val channelName: String,
    val archived: Boolean,
    /** Whether the bot may add and remove members there. */
    val manageable: Boolean,
)

@Serializable
data class AssignThreadRequest(
    val threadId: String,
    val countryId: String,
)

@Serializable
data class AddMemberRequest(
    val discordUserId: String,
)
