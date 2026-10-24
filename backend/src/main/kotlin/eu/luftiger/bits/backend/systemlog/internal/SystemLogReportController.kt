package eu.luftiger.bits.backend.systemlog.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.RoleSyncReport
import eu.luftiger.bits.shared.UndeliveredEventReport
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** What the bot reports for the system log; behind the service token like everything under `/api`. */
@RestController
@RequestMapping("/api/system-log")
class SystemLogReportController(
    private val service: SystemLogService,
) {
    @PostMapping("/role-syncs/{syncId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun roleSync(
        @PathVariable syncId: Long,
        @RequestBody report: RoleSyncReport,
    ) {
        BotInput.reportedChangeCount(report.changes.size)
        report.changes.forEach {
            BotInput.discordUserId(it.discordUserId)
            BotInput.memberName(it.memberName)
            BotInput.roleId(it.roleId)
            it.roleName?.let(BotInput::roleName)
            it.reason?.let(BotInput::reason)
        }
        service.applyReport(syncId, report)
    }

    @PostMapping("/undelivered-events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun undeliveredEvent(
        @RequestBody report: UndeliveredEventReport,
    ) {
        BotInput.streamId(report.streamId)
        report.eventType?.let(BotInput::eventType)
        service.recordUndelivered(report)
    }
}
