package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.vote.domain.Vote
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * Votes in the dashboard, for admins and members of the roles that may manage votes; the roles come from the session,
 * see [SessionPrincipal.discordRoleIds]. Everybody logged in may ask whether they are one of them.
 */
@RestController
@RequestMapping("/web/votes")
class VoteWebController(
    private val voteService: VoteService,
    private val channelService: DiscordChannelService,
    private val guildRoles: GuildRoles,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/access")
    fun access(
        @AuthenticationPrincipal session: Jwt,
    ): VoteAccessResponse {
        val manager = manager(session)
        return VoteAccessResponse(canManage = voteService.mayManage(manager), canConfigure = manager.admin)
    }

    @GetMapping
    fun votes(
        @AuthenticationPrincipal session: Jwt,
    ): VotesResponse {
        requireManager(session)
        val closed = voteService.closed()
        val results = voteService.results(closed)
        return VotesResponse(
            open = voteService.open().map { it.toResponse(voted = voteService.votedCount(requireNotNull(it.id))) },
            closed =
                closed.map {
                    it.toResponse(
                        resultText =
                            results[it.id]?.let { result ->
                                VoteResultRenderer.text(result, markdown = false)
                            },
                    )
                },
        )
    }

    @GetMapping("/options")
    fun options(
        @AuthenticationPrincipal session: Jwt,
    ): VoteOptionsResponse {
        requireManager(session)
        return VoteOptionsResponse(
            roles = guildRoles.all().map { RoleOption(it.id, it.name, it.color) },
            channels = channelService.postable().map { ChannelOption(it.id, it.name, it.category) },
        )
    }

    /** 409 if the bot cannot post in the channel. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @RequestBody request: CreateVoteRequest,
        @AuthenticationPrincipal session: Jwt,
    ): VoteResponse {
        val manager = manager(session)
        val endsAt = request.endsAt?.let(Instant::ofEpochMilli)
        return when (val outcome = voteService.start(manager, request.question, request.roleId, request.channelId, endsAt)) {
            is StartOutcome.Started -> {
                log.info("Vote {} started by {}", outcome.vote.id, session.subject)
                outcome.vote.toResponse(voted = 0)
            }
            StartOutcome.Forbidden -> throw ResponseStatusException(HttpStatus.FORBIDDEN)
            StartOutcome.ChannelNotPostable -> throw ResponseStatusException(HttpStatus.CONFLICT, "Channel not postable")
        }
    }

    /** 409 if the vote has ended already. */
    @PostMapping("/{id}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun end(
        @PathVariable id: Long,
        @AuthenticationPrincipal session: Jwt,
    ) = close(id, session, cancel = false)

    /** 409 if the vote has ended already. */
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @PathVariable id: Long,
        @AuthenticationPrincipal session: Jwt,
    ) = close(id, session, cancel = true)

    @GetMapping("/{id}/result.png", produces = [MediaType.IMAGE_PNG_VALUE])
    fun resultImage(
        @PathVariable id: Long,
        @AuthenticationPrincipal session: Jwt,
    ): ResponseEntity<ByteArray> {
        requireManager(session)
        val (_, result) = voteService.result(id)
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(VoteResultRenderer.png(result))
    }

    private fun close(
        id: Long,
        session: Jwt,
        cancel: Boolean,
    ) {
        when (voteService.close(id, manager(session), cancel)) {
            CloseOutcome.Closed -> log.info("Vote {} {} by {}", id, if (cancel) "cancelled" else "ended", session.subject)
            CloseOutcome.Forbidden -> throw ResponseStatusException(HttpStatus.FORBIDDEN)
            CloseOutcome.NotOpen -> throw ResponseStatusException(HttpStatus.CONFLICT, "Vote $id is not open")
        }
    }

    private fun requireManager(session: Jwt) {
        if (!voteService.mayManage(manager(session))) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun manager(session: Jwt) =
        SessionPrincipal.of(session).let { VoteManager(it.subject, it.name, it.avatarUrl, it.admin, it.discordRoleIds) }

    private fun Vote.toResponse(
        voted: Int? = null,
        resultText: String? = null,
    ) = VoteResponse(
        id = requireNotNull(id),
        question = question,
        roleName = roleName,
        channelName = channelName,
        startedAt = startedAt.toEpochMilli(),
        startedBy = startedByName,
        endsAt = endsAt?.toEpochMilli(),
        closedAt = closedAt?.toEpochMilli(),
        closedBy = closedByName,
        status = VoteStatusResponse.of(status),
        voted = voted,
        eligible = eligibleCount,
        resultText = resultText,
    )
}

/** Admins only: which roles may manage votes besides them. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/votes")
class VoteAdminController(
    private val voteService: VoteService,
) {
    @GetMapping("/manager-roles")
    fun managerRoles() = ManagerRolesRequest(voteService.managerRoleIds())

    @PutMapping("/manager-roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setManagerRoles(
        @RequestBody request: ManagerRolesRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { VoteManager(it.subject, it.name, it.avatarUrl, it.admin, it.discordRoleIds) }
        voteService.setManagerRoleIds(request.roleIds, actor.auditActor())
    }
}

@Serializable
data class VoteAccessResponse(
    /** Whether the user may see, start, end and cancel votes. */
    val canManage: Boolean,
    /** Whether the user may choose the roles that manage votes, i.e. is an admin. */
    val canConfigure: Boolean,
)

@Serializable
data class VotesResponse(
    /** Newest first. */
    val open: List<VoteResponse>,
    /** Newest end first. */
    val closed: List<VoteResponse>,
)

@Serializable
data class VoteResponse(
    val id: Long,
    val question: String,
    /** Snapshots from the start; `null` if BITS did not know the name. */
    val roleName: String?,
    val channelName: String?,
    /** Epoch milliseconds, like the other times. */
    val startedAt: Long,
    val startedBy: String,
    val endsAt: Long?,
    val closedAt: Long?,
    /** `null` if it ended on its own. */
    val closedBy: String?,
    val status: VoteStatusResponse,
    /** Ballots so far; only for open votes. */
    val voted: Int?,
    /** Members of the role with a linked account, as the bot last reported; `null` until it did. */
    val eligible: Int?,
    /** Only for evaluated votes; the image is at `/web/votes/{id}/result.png`. */
    val resultText: String?,
)

/** The API's names for [VoteStatus]; the dashboard spells them out in `frontend/src/lib/api.ts`. */
@Serializable
enum class VoteStatusResponse {
    OPEN,
    COUNTING,
    EVALUATED,
    CANCELLED,
    ;

    companion object {
        fun of(status: VoteStatus) =
            when (status) {
                VoteStatus.OPEN -> OPEN
                VoteStatus.COUNTING -> COUNTING
                VoteStatus.EVALUATED -> EVALUATED
                VoteStatus.CANCELLED -> CANCELLED
            }
    }
}

@Serializable
data class VoteOptionsResponse(
    /** Highest first. */
    val roles: List<RoleOption>,
    /** Only those the bot may post in, in Discord's order. */
    val channels: List<ChannelOption>,
)

@Serializable
data class RoleOption(
    val id: String,
    val name: String,
    val color: Int?,
)

@Serializable
data class ChannelOption(
    val id: String,
    val name: String,
    val category: String?,
)

@Serializable
data class CreateVoteRequest(
    val question: String,
    val roleId: String,
    val channelId: String,
    /** Epoch milliseconds, `null` for a vote without end. */
    val endsAt: Long?,
)

@Serializable
data class ManagerRolesRequest(
    val roleIds: List<String>,
)
