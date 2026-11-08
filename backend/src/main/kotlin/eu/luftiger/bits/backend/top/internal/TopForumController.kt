package eu.luftiger.bits.backend.top.internal

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

/** The TOP forums in the dashboard; only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/tops")
class TopForumController(
    private val service: TopService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun settings() =
        TopSettingsView(
            forums =
                service.forums().map { forum ->
                    TopForumOption(
                        forum.id,
                        forum.name,
                        forum.category,
                        forum.threadsManageable,
                        forum.tags.map { TopTagOption(it.id, it.name) },
                    )
                },
            selected = service.topForums().map { TopForumSetting(it.forumId, it.doneTagId) },
        )

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setForums(
        @Valid @RequestBody request: TopForumsRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        service.setTopForums(request.forums.map { TopForumSelection(it.forumId, it.doneTagId) }, actor)
        log.info("TOP forums set by {}", session.subject)
    }
}

@Serializable
data class TopSettingsView(
    /** Forum channels in the order of Discord. */
    val forums: List<TopForumOption>,
    val selected: List<TopForumSetting>,
)

@Serializable
data class TopForumOption(
    val id: String,
    val name: String,
    /** `null` for none. */
    val category: String?,
    /** Whether the bot may lock, archive and tag the posts there. */
    val usable: Boolean,
    val tags: List<TopTagOption>,
)

@Serializable
data class TopTagOption(
    val id: String,
    val name: String,
)

@Serializable
data class TopForumSetting(
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val forumId: String,
    /** `null` for no tag. */
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val doneTagId: String?,
)

@Serializable
data class TopForumsRequest(
    @field:Size(max = MAX_TOP_FORUMS)
    val forums: List<@Valid TopForumSetting>,
)

/** Far more forums than a guild has for its agendas. */
private const val MAX_TOP_FORUMS = 50
