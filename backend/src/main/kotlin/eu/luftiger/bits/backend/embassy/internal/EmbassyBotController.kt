package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.shared.DiscordThreadsReport
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadCreationReport
import eu.luftiger.bits.shared.EmbassyThreadRenameReport
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** What the bot reports for the embassies; behind the service token like everything under `/api`. */
@RestController
class EmbassyBotController(
    private val reportService: EmbassyReportService,
    private val creationService: EmbassyThreadCreationService,
    private val renameService: EmbassyThreadRenameService,
    private val syncTrigger: EmbassySyncTrigger,
) {
    /** The private threads of the guild, on start, every hour and whenever a thread or channel changes. */
    @PutMapping("/api/discord-threads")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun replaceThreads(
        @RequestBody report: DiscordThreadsReport,
    ) {
        BotInput.threadCount(report.threads.size)
        report.threads.forEach {
            BotInput.channelId(it.id)
            BotInput.channelName(it.name)
            BotInput.channelId(it.parentChannelId)
            BotInput.channelName(it.parentChannelName)
            BotInput.roleCount(it.viewerRoleIds.size)
            it.viewerRoleIds.forEach(BotInput::roleId)
        }
        reportService.replaceThreads(report.threads)
    }

    @PostMapping("/api/embassies/syncs/{syncId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun sync(
        @PathVariable syncId: Long,
        @RequestBody report: EmbassySyncReport,
    ) {
        BotInput.threadCount(report.threads.size)
        report.threads.forEach { thread ->
            BotInput.channelId(thread.threadId)
            thread.threadName?.let(BotInput::channelName)
            BotInput.memberCount(thread.members.size)
            thread.members.forEach {
                BotInput.discordUserId(it.discordUserId)
                BotInput.memberName(it.name)
                BotInput.reason(it.reason)
            }
        }
        BotInput.reportedChangeCount(report.changes.size)
        report.changes.forEach {
            BotInput.discordUserId(it.discordUserId)
            BotInput.memberName(it.memberName)
            it.threadId?.let(BotInput::channelId)
            it.threadName?.let(BotInput::channelName)
            it.roleId?.let(BotInput::roleId)
            it.roleName?.let(BotInput::roleName)
            it.reason?.let(BotInput::reason)
        }
        reportService.applyReport(syncId, report)
    }

    /** The thread the bot created for an admin, or why it could not; 410 if it is no longer wanted. */
    @PostMapping("/api/embassies/thread-creations/{creationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun threadCreated(
        @PathVariable creationId: Long,
        @RequestBody report: EmbassyThreadCreationReport,
    ) {
        report.thread?.let {
            BotInput.channelId(it.id)
            BotInput.channelName(it.name)
            BotInput.channelId(it.parentChannelId)
            BotInput.channelName(it.parentChannelName)
        }
        // Synced right away, so the members are in before the next hourly sync.
        if (creationService.applyReport(creationId, report)) syncTrigger.request(SyncTrigger.SETTING_CHANGE)
    }

    /** The threads the bot could not rename after an admin asked for it. */
    @PostMapping("/api/embassies/thread-renames")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun threadsRenamed(
        @RequestBody report: EmbassyThreadRenameReport,
    ) {
        BotInput.threadCount(report.failures.size)
        report.failures.forEach {
            BotInput.channelId(it.threadId)
            it.threadName?.let(BotInput::channelName)
            BotInput.countryName(it.countryName)
        }
        renameService.applyReport(report)
    }
}
