package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggles
import eu.luftiger.bits.backend.members.api.GuildMembers
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.shared.WebInput
import eu.luftiger.bits.backend.vote.domain.Choice
import eu.luftiger.bits.backend.vote.domain.Vote
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
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
 * Votes in the dashboard, for admins and members of the roles that may manage votes. Everybody logged in may ask
 * whether they are one of them.
 *
 * The roles are the ones the bot reported last, see [GuildMembers], so a member who loses a manager role in Discord
 * loses access right away rather than when the session ends. Only until the bot has reported the roles since the
 * backend started are the roles from the login used, see [SessionPrincipal.discordRoleIds].
 */
@RestController
@RequestMapping("/web/votes")
class VoteWebController(
    private val voteService: VoteService,
    private val voteManagerRoles: VoteManagerRoles,
    private val channelService: DiscordChannelService,
    private val guildRoles: GuildRoles,
    private val settingsService: VoteResultSettingsService,
    private val features: FeatureToggles,
    private val guildMembers: GuildMembers,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/access")
    fun access(
        @AuthenticationPrincipal session: Jwt,
    ): VoteAccessResponse = VoteAccessResponse(canManage = voteManagerRoles.mayManage(manager(session)))

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
                                VoteResultRenderer.text(result)
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

    /**
     * 409 if the bot cannot post in the channel, 423 while an admin has switched votes off. The bot checks the switch
     * itself before it starts a vote from Discord.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @Valid @RequestBody request: CreateVoteRequest,
        @AuthenticationPrincipal session: Jwt,
    ): VoteResponse {
        if (!features.isEnabled(BitsFeature.VOTES)) throw ResponseStatusException(HttpStatus.LOCKED, "Votes are switched off")
        val manager = manager(session)
        val endsAt = request.endsAt?.let(Instant::ofEpochMilli)
        return when (
            val outcome =
                voteService.start(manager, request.question, request.roleId, request.channelId, endsAt, request.threshold?.toThreshold())
        ) {
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
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(VoteResultRenderer.png(result, settingsService.style()))
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
        if (!voteManagerRoles.mayManage(manager(session))) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    private fun manager(session: Jwt): VoteManager {
        val principal = SessionPrincipal.of(session)
        return VoteManager(principal.subject, principal.name, principal.avatarUrl, principal.admin, currentRoleIds(principal))
    }

    /** A member missing from the bot's list has left the guild and holds no roles. */
    private fun currentRoleIds(principal: SessionPrincipal): List<String> {
        val discordUserId = principal.discordUserId ?: return emptyList()
        val members = guildMembers.members() ?: return principal.discordRoleIds
        return members
            .firstOrNull { it.discordUserId == discordUserId }
            ?.roleIds
            ?.toList()
            .orEmpty()
    }

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
        threshold = thresholdPercent?.let { VoteThresholdBody(it, ThresholdChoice.of(requireNotNull(thresholdChoice))) },
        endedByThreshold = endedByThreshold(),
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
    private val voteManagerRoles: VoteManagerRoles,
) {
    @GetMapping("/manager-roles")
    fun managerRoles() = ManagerRolesRequest(voteManagerRoles.managerRoleIds())

    @PutMapping("/manager-roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setManagerRoles(
        @Valid @RequestBody request: ManagerRolesRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val actor = SessionPrincipal.of(session).let { VoteManager(it.subject, it.name, it.avatarUrl, it.admin, it.discordRoleIds) }
        voteManagerRoles.setManagerRoleIds(request.roleIds, actor.auditActor())
    }
}

@Serializable
data class VoteAccessResponse(
    /** Whether the user may see, start, end and cancel votes. */
    val canManage: Boolean,
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
    /** At which the vote ends early, `null` if it does not. */
    val threshold: VoteThresholdBody?,
    /** Whether it ended because it reached [threshold]. */
    val endedByThreshold: Boolean,
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
    @field:Size(max = VoteService.MAX_QUESTION_LENGTH)
    val question: String,
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val roleId: String,
    @field:Size(max = WebInput.MAX_ID_LENGTH)
    val channelId: String,
    /** Epoch milliseconds, `null` for a vote without end. */
    val endsAt: Long?,
    /** Ends the vote early, whichever comes first with [endsAt]; `null` if it does not. */
    val threshold: VoteThresholdBody? = null,
)

/** The vote ends early once more than [percent] (1 to 99) of the eligible members chose [choice]. */
@Serializable
data class VoteThresholdBody(
    val percent: Int,
    val choice: ThresholdChoice,
) {
    fun toThreshold() = Threshold(percent, choice.toChoice())
}

/** The options a threshold can be for; abstaining decides nothing. */
@Serializable
enum class ThresholdChoice {
    YES,
    NO,
    ;

    fun toChoice() =
        when (this) {
            YES -> Choice.YES
            NO -> Choice.NO
        }

    companion object {
        fun of(choice: Choice) =
            when (choice) {
                Choice.YES -> YES
                Choice.NO -> NO
                Choice.ABSTAIN -> error("A threshold is for Yes or No only")
            }
    }
}

@Serializable
data class ManagerRolesRequest(
    @field:Size(max = WebInput.MAX_ROLES)
    val roleIds: List<
        @Size(max = WebInput.MAX_ID_LENGTH)
        String,
    >,
)
