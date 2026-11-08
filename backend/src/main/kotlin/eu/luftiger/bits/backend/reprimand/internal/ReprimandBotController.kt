package eu.luftiger.bits.backend.reprimand.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.ProtectedCause
import eu.luftiger.bits.shared.ReprimandMember
import eu.luftiger.bits.shared.ReprimandRequest
import eu.luftiger.bits.shared.ReprimandResponse
import eu.luftiger.bits.shared.ReprimandTimeoutProblemReport
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** `/reprimand` from the bot, and what went wrong with a timeout; behind the service token like everything under `/api`. */
@RestController
@RequestMapping("/api/reprimands")
class ReprimandBotController(
    private val service: ReprimandService,
) {
    @PostMapping
    fun reprimand(
        @RequestBody request: ReprimandRequest,
    ): ReprimandResponse {
        BotInput.reason(request.reason)
        return when (val outcome = service.reprimand(party(request.actor), party(request.target), request.reason)) {
            is ReprimandOutcome.Reprimanded ->
                ReprimandResponse.Reprimanded(
                    outcome.reprimandId,
                    outcome.count,
                    outcome.timeoutMinutes,
                    outcome.timeoutUntil.toEpochMilli(),
                )
            ReprimandOutcome.Forbidden -> ReprimandResponse.Forbidden
            ReprimandOutcome.Disabled -> ReprimandResponse.Disabled
            is ReprimandOutcome.Protected ->
                ReprimandResponse.Protected(
                    when (outcome.reason) {
                        ProtectedReason.SELF -> ProtectedCause.SELF
                        ProtectedReason.BOT -> ProtectedCause.BOT
                        ProtectedReason.ADMIN -> ProtectedCause.ADMIN
                        ProtectedReason.REPRIMANDER -> ProtectedCause.REPRIMANDER
                    },
                )
        }
    }

    @PostMapping("/timeout-problems")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun timeoutProblem(
        @RequestBody report: ReprimandTimeoutProblemReport,
    ) {
        BotInput.discordUserId(report.discordUserId)
        BotInput.memberName(report.memberName)
        BotInput.reason(report.reason)
        service.timeoutFailed(report.discordUserId, report.memberName, report.reason, report.reprimandId)
    }

    private fun party(member: ReprimandMember): ReprimandParty {
        BotInput.discordUserId(member.discordUserId)
        BotInput.memberName(member.name)
        member.avatarUrl?.let(BotInput::avatarUrl)
        BotInput.roleCount(member.roleIds.size)
        member.roleIds.forEach(BotInput::roleId)
        return ReprimandParty(member.discordUserId, member.name, member.avatarUrl, member.roleIds, member.administrator, member.bot)
    }
}
