package eu.luftiger.bits.backend.honeypot.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.HoneypotKickReport
import eu.luftiger.bits.shared.HoneypotPauseReport
import eu.luftiger.bits.shared.HoneypotProblemReport
import eu.luftiger.bits.shared.HoneypotState
import eu.luftiger.bits.shared.HoneypotWarningReport
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** What the bot asks and reports about the honeypot; behind the service token like everything under `/api`. */
@RestController
@RequestMapping("/api/honeypot")
class HoneypotBotController(
    private val service: HoneypotService,
) {
    @GetMapping
    fun state(): HoneypotState = service.state()

    @PutMapping("/warning")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun warning(
        @RequestBody report: HoneypotWarningReport,
    ) {
        report.warning?.let {
            BotInput.channelId(it.channelId)
            BotInput.messageId(it.messageId)
        }
        service.recordWarning(report)
    }

    @PostMapping("/kicks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun kick(
        @RequestBody report: HoneypotKickReport,
    ) {
        BotInput.discordUserId(report.discordUserId)
        BotInput.memberName(report.memberName)
        report.reason?.let(BotInput::reason)
        service.recordKick(report)
    }

    @PostMapping("/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun pause(
        @RequestBody report: HoneypotPauseReport,
    ) {
        BotInput.channelId(report.channelId)
        service.pauseKicks(report)
    }

    @PostMapping("/problems")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun problem(
        @RequestBody report: HoneypotProblemReport,
    ) {
        BotInput.reason(report.reason)
        service.recordProblem(report)
    }
}
