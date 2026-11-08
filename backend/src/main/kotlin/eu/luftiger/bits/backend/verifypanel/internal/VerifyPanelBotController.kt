package eu.luftiger.bits.backend.verifypanel.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.VerifyPanelProblemReport
import eu.luftiger.bits.shared.VerifyPanelReport
import eu.luftiger.bits.shared.VerifyPanelState
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** What the bot asks and reports about the message with the button to verify; behind the service token like everything under `/api`. */
@RestController
@RequestMapping("/api/verify-panel")
class VerifyPanelBotController(
    private val service: VerifyPanelService,
) {
    @GetMapping
    fun state(): VerifyPanelState = service.state()

    @PutMapping("/message")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun message(
        @RequestBody report: VerifyPanelReport,
    ) {
        report.message?.let {
            BotInput.channelId(it.channelId)
            BotInput.messageId(it.messageId)
        }
        service.recordMessage(report)
    }

    @PostMapping("/problems")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun problem(
        @RequestBody report: VerifyPanelProblemReport,
    ) {
        BotInput.reason(report.reason)
        service.recordProblem(report)
    }
}
