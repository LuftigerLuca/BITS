package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.backend.vote.domain.Choice
import eu.luftiger.bits.backend.vote.domain.Elector
import eu.luftiger.bits.backend.vote.domain.Vote
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import eu.luftiger.bits.shared.CastVoteRequest
import eu.luftiger.bits.shared.CastVoteResponse
import eu.luftiger.bits.shared.CloseVoteRequest
import eu.luftiger.bits.shared.CloseVoteResponse
import eu.luftiger.bits.shared.ElectorateReport
import eu.luftiger.bits.shared.OpenVote
import eu.luftiger.bits.shared.OpenVotesResponse
import eu.luftiger.bits.shared.ParticipationRequest
import eu.luftiger.bits.shared.ParticipationResponse
import eu.luftiger.bits.shared.PartyResultDto
import eu.luftiger.bits.shared.StartVoteRequest
import eu.luftiger.bits.shared.StartVoteResponse
import eu.luftiger.bits.shared.VoteActor
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteMessageReport
import eu.luftiger.bits.shared.VoteResultDto
import eu.luftiger.bits.shared.VoteState
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Base64

/**
 * Votes from Discord: `/abstimmung` and the buttons, and what the bot reports back for the events of a vote. Whether
 * the actor may manage votes is decided here, from the roles the bot sends along; that the member clicking a button
 * has the role is checked by the bot.
 */
@RestController
@RequestMapping("/api/votes")
class VoteBotController(
    private val voteService: VoteService,
    private val settingsService: VoteResultSettingsService,
) {
    @PostMapping
    fun start(
        @RequestBody request: StartVoteRequest,
    ): StartVoteResponse {
        check(request.actor)
        BotInput.roleId(request.roleId)
        BotInput.channelId(request.channelId)
        return when (
            val outcome =
                voteService.start(
                    manager(request.actor),
                    request.question,
                    request.roleId,
                    request.channelId,
                    request.endsAt?.let(Instant::ofEpochMilli),
                    request.autoClosePercent,
                )
        ) {
            is StartOutcome.Started -> StartVoteResponse.Started(requireNotNull(outcome.vote.id))
            StartOutcome.Forbidden -> StartVoteResponse.Forbidden
            StartOutcome.ChannelNotPostable -> StartVoteResponse.ChannelNotPostable
        }
    }

    @GetMapping("/open")
    fun open() = OpenVotesResponse(voteService.open().map { OpenVote(requireNotNull(it.id), it.question) })

    @GetMapping("/{id}")
    fun vote(
        @PathVariable id: Long,
    ): VoteDto = voteService.find(id).toDto()

    @PostMapping("/{id}/close")
    fun close(
        @PathVariable id: Long,
        @RequestBody request: CloseVoteRequest,
    ): CloseVoteResponse {
        check(request.actor)
        return when (voteService.close(id, manager(request.actor), request.cancel)) {
            CloseOutcome.Closed -> CloseVoteResponse.Closed
            CloseOutcome.Forbidden -> CloseVoteResponse.Forbidden
            CloseOutcome.NotOpen -> CloseVoteResponse.NotOpen
        }
    }

    @PostMapping("/{id}/ballots")
    fun cast(
        @PathVariable id: Long,
        @RequestBody request: CastVoteRequest,
    ): CastVoteResponse {
        BotInput.discordUserId(request.discordUserId)
        return when (val outcome = voteService.cast(id, request.discordUserId, request.choice.toChoice())) {
            is CastOutcome.Cast -> CastVoteResponse.Cast(outcome.previous?.toVoteChoice())
            CastOutcome.NotLinked -> CastVoteResponse.NotLinked
            CastOutcome.Closed -> CastVoteResponse.Closed
        }
    }

    @PostMapping("/{id}/participation")
    fun participation(
        @PathVariable id: Long,
        @RequestBody request: ParticipationRequest,
    ): ParticipationResponse {
        BotInput.memberCount(request.roleMemberIds.size)
        request.roleMemberIds.forEach(BotInput::discordUserId)
        val participation = voteService.participation(id, request.roleMemberIds)
        return ParticipationResponse(participation.voted, participation.eligible)
    }

    @PutMapping("/{id}/message")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun message(
        @PathVariable id: Long,
        @RequestBody report: VoteMessageReport,
    ) {
        BotInput.messageId(report.messageId)
        voteService.reportMessage(id, report.messageId)
    }

    @PutMapping("/{id}/electorate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun electorate(
        @PathVariable id: Long,
        @RequestBody report: ElectorateReport,
    ) {
        BotInput.memberCount(report.members.size)
        report.members.forEach {
            BotInput.discordUserId(it.discordUserId)
            BotInput.memberName(it.name)
        }
        voteService.reportElectorate(id, report.members.map { Elector(id, it.discordUserId, it.name) })
    }

    @GetMapping("/{id}/result")
    fun result(
        @PathVariable id: Long,
    ): VoteResultDto {
        val (vote, result) = voteService.result(id)
        return VoteResultDto(
            question = result.question,
            parties = result.parties.map { PartyResultDto(it.partyName, it.yes, it.no, it.abstain, it.eligible) },
            nonVoters = result.nonVoters,
            image = Base64.getEncoder().encodeToString(VoteResultRenderer.png(result, settingsService.style())),
            posted = vote.resultPostedAt != null,
        )
    }

    @PostMapping("/{id}/result/posted")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resultPosted(
        @PathVariable id: Long,
    ) = voteService.markResultPosted(id)

    private fun check(actor: VoteActor) {
        BotInput.discordUserId(actor.discordUserId)
        BotInput.memberName(actor.name)
        BotInput.roleCount(actor.roleIds.size)
        actor.roleIds.forEach(BotInput::roleId)
    }

    private fun manager(actor: VoteActor) =
        VoteManager("discord:${actor.discordUserId}", actor.name, actor.avatarUrl, actor.administrator, actor.roleIds)

    private fun Vote.toDto() =
        VoteDto(
            id = requireNotNull(id),
            question = question,
            roleId = roleId,
            channelId = channelId,
            endsAt = endsAt?.toEpochMilli(),
            autoClosePercent = autoClosePercent,
            endedByParticipation = endedByParticipation(),
            state =
                when (status) {
                    VoteStatus.OPEN -> VoteState.OPEN
                    VoteStatus.COUNTING -> VoteState.COUNTING
                    VoteStatus.EVALUATED -> VoteState.EVALUATED
                    VoteStatus.CANCELLED -> VoteState.CANCELLED
                },
            messageId = messageId,
        )

    private fun VoteChoice.toChoice() =
        when (this) {
            VoteChoice.YES -> Choice.YES
            VoteChoice.NO -> Choice.NO
            VoteChoice.ABSTAIN -> Choice.ABSTAIN
        }

    private fun Choice.toVoteChoice() =
        when (this) {
            Choice.YES -> VoteChoice.YES
            Choice.NO -> VoteChoice.NO
            Choice.ABSTAIN -> VoteChoice.ABSTAIN
        }
}
