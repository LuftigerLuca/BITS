package eu.luftiger.bits.backend.memberadmin.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.shared.WebInput
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The page "Mitglieder" in the dashboard. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/members")
class MemberAdminController(
    private val service: MemberAdminService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun search(
        @RequestParam @Size(max = WebInput.MAX_QUERY_LENGTH) query: String,
    ): List<MemberSummary> = service.search(query)

    /** 404 for a member who is neither on the guild nor linked. */
    @GetMapping("/{discordUserId}")
    fun member(
        @PathVariable @Size(max = WebInput.MAX_ID_LENGTH) discordUserId: String,
    ): MemberDetailResponse = service.member(discordUserId)

    /** Judged with fresh WarEra data like `/rollen`, so it can take a while; 503 if WarEra cannot be reached. */
    @GetMapping("/{discordUserId}/roles")
    fun roles(
        @PathVariable @Size(max = WebInput.MAX_ID_LENGTH) discordUserId: String,
    ): MemberRolesResponse = service.roles(discordUserId)

    /** 404 for an unknown member, 409 while nothing is synced, since the role sync and all it sets is switched off. */
    @PostMapping("/{discordUserId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun sync(
        @PathVariable @Size(max = WebInput.MAX_ID_LENGTH) discordUserId: String,
        @AuthenticationPrincipal session: Jwt,
    ) {
        service.sync(discordUserId, actor(session))
        log.info("Sync of member {} requested by {}", discordUserId, session.subject)
    }

    /** Works like `/unverify`; 404 if there is no link. */
    @DeleteMapping("/{discordUserId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlink(
        @PathVariable @Size(max = WebInput.MAX_ID_LENGTH) discordUserId: String,
        @AuthenticationPrincipal session: Jwt,
    ) {
        service.unlink(discordUserId, actor(session))
        log.info("Link of member {} deleted by {}", discordUserId, session.subject)
    }

    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
}

@Serializable
data class MemberSummary(
    val discordUserId: String,
    /** As the bot reported it last; `null` if it has not. */
    val discordName: String?,
    /** Name at the time of linking; `null` without a link. */
    val wareraUsername: String?,
    /** `null` until the bot has reported the members since the backend started. */
    val onServer: Boolean?,
)

@Serializable
data class MemberDetailResponse(
    val discordUserId: String,
    /** As the bot reported it last, or as the system log kept it for a member who left. */
    val discordName: String?,
    val onServer: Boolean?,
    val link: MemberLinkResponse?,
    /** What the system log keeps about the member, newest first, only the last few. */
    val recentLog: List<MemberLogEventResponse>,
)

@Serializable
data class MemberLinkResponse(
    val wareraUserId: String,
    val wareraUsername: String,
    /** Epoch milliseconds. */
    val verifiedAt: Long,
)

/** An entry about the member's account, or a change of a sync for them, which has an [outcome]. */
@Serializable
data class MemberLogEventResponse(
    /** Epoch milliseconds. */
    val occurredAt: Long,
    /** The type of the system log entry, e.g. `SYNC_RUN` or `ACCOUNT_LINKED`. */
    val kind: String,
    /** The trigger of the sync, e.g. `HOURLY`; `null` for anything else. */
    val syncTrigger: String?,
    /** Name of the role, as last seen. */
    val role: String?,
    /** E.g. `GRANTED`; `null` for an entry about the account. */
    val outcome: String?,
    val reason: String?,
    val wareraUsername: String?,
    /** Whether the member is the one the WarEra account was taken from. */
    val previousAccount: Boolean,
)

@Serializable
data class MemberRolesResponse(
    val linked: Boolean,
    /** Every role BITS manages, highest in the guild first; empty if the member is not linked or there are no rules. */
    val roles: List<MemberRoleResponse>,
)

@Serializable
data class MemberRoleResponse(
    val id: String,
    val name: String,
    val color: Int?,
    val outcome: MemberRoleOutcome,
    /** Whether the member holds the role now, as the bot reported it last; `null` if it has not. */
    val held: Boolean?,
    /** The rules of the role, any one of which is enough. */
    val rules: List<MemberRuleResponse>,
)

@Serializable
enum class MemberRoleOutcome {
    MET,
    NOT_MET,
    UNDECIDED,
}

@Serializable
data class MemberRuleResponse(
    val label: String,
    val met: Boolean?,
    val detail: String?,
)
