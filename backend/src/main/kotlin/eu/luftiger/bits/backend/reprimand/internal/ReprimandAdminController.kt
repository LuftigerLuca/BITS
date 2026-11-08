package eu.luftiger.bits.backend.reprimand.internal

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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The reprimands and their settings in the dashboard; only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/reprimands")
class ReprimandAdminController(
    private val service: ReprimandService,
    private val settings: ReprimandSettings,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun reprimands(): ReprimandsView {
        val all = service.all()
        val counts =
            all
                .filter { !it.withdrawn }
                .groupBy { it.discordUserId }
                // The newest name first, as the list is sorted that way.
                .map { (id, reprimands) -> ReprimandCountView(id, reprimands.first().memberName, reprimands.size) }
                .sortedWith(compareByDescending<ReprimandCountView> { it.count }.thenBy { it.memberName.lowercase() })
        return ReprimandsView(
            reprimands =
                all.map {
                    ReprimandView(
                        id = requireNotNull(it.id),
                        discordUserId = it.discordUserId,
                        memberName = it.memberName,
                        reason = it.reason,
                        actorName = it.actorName,
                        createdAt = it.createdAt.toEpochMilli(),
                        withdrawnAt = it.withdrawnAt?.toEpochMilli(),
                        withdrawnBy = it.withdrawnByName,
                    )
                },
            counts = counts,
        )
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(
        @PathVariable id: Long,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        service.withdraw(id, actor)
        log.info("Reprimand {} withdrawn by {}", id, session.subject)
    }

    @GetMapping("/settings")
    fun settings() =
        ReprimandSettingsView(
            roles = settings.roles().map { ReprimandRoleOption(it.id, it.name, it.color) },
            roleIds = settings.reprimandRoleIds(),
            timeoutMinutes = settings.timeoutMinutes(),
        )

    @PutMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setSettings(
        @Valid @RequestBody request: ReprimandSettingsRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        settings.set(request.roleIds, request.timeoutMinutes, actor)
        log.info("Reprimand settings set by {}", session.subject)
    }
}

@Serializable
data class ReprimandsView(
    /** The newest first, withdrawn ones too. */
    val reprimands: List<ReprimandView>,
    /** Reprimands that count per member, the most first. */
    val counts: List<ReprimandCountView>,
)

@Serializable
data class ReprimandView(
    val id: Long,
    val discordUserId: String,
    /** As Discord showed the member then. */
    val memberName: String,
    val reason: String,
    val actorName: String,
    val createdAt: Long,
    /** `null` while it counts. */
    val withdrawnAt: Long?,
    val withdrawnBy: String?,
)

@Serializable
data class ReprimandCountView(
    val discordUserId: String,
    /** As of the newest reprimand. */
    val memberName: String,
    val count: Int,
)

@Serializable
data class ReprimandSettingsView(
    /** The roles of the guild, highest first. */
    val roles: List<ReprimandRoleOption>,
    /** The roles whose members may reprimand besides the admins. */
    val roleIds: List<String>,
    val timeoutMinutes: Int,
)

@Serializable
data class ReprimandRoleOption(
    val id: String,
    val name: String,
    val color: Int?,
)

@Serializable
data class ReprimandSettingsRequest(
    @field:Size(max = WebInput.MAX_ROLES)
    val roleIds: List<
        @Size(max = WebInput.MAX_ID_LENGTH)
        String,
    >,
    /** Checked by [ReprimandSettings.set]. */
    val timeoutMinutes: Int,
)
