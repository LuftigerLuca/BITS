package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.embassy.domain.EmbassySetting
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.systemlog.api.EmbassyThreadCause
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.shared.EmbassyThreadRename
import eu.luftiger.bits.shared.EmbassyThreadRenameEvent
import eu.luftiger.bits.shared.EmbassyThreadRenameReport
import eu.luftiger.bits.shared.ThreadRenameFailure
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Renames the assigned embassy threads after the name template, only when an admin asks for it. Fire-and-forget: the
 * bot reports only what failed, and the threads it renamed show with their new names once it reports them again.
 * Archived threads are skipped, since renaming would unarchive them; the dashboard marks them.
 */
@Service
class EmbassyThreadRenameService(
    private val threadRepository: EmbassyThreadRepository,
    private val discordThreadRepository: DiscordThreadRepository,
    private val settingRepository: EmbassySettingRepository,
    private val eventPublisher: EventPublisher,
    private val auditLog: AuditLog,
    private val systemLog: SystemLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Asks the bot to rename every assigned thread that it last reported as not archived and named other than after
     * the template. Returns how many that are; with none, nothing is published, but the request is still recorded.
     */
    fun request(actor: AuditActor): Int =
        transactionTemplate.execute {
            val template =
                settingRepository.findByIdOrNull(EmbassySetting.SINGLE_ROW_ID)?.threadNameTemplate ?: EmbassyThreadName.DEFAULT_TEMPLATE
            val reported = discordThreadRepository.findAll().associateBy { it.id }
            val renames =
                threadRepository
                    .findAll()
                    .sortedBy { it.countryName }
                    .mapNotNull { thread ->
                        val current = reported[thread.threadId] ?: return@mapNotNull null
                        val name = EmbassyThreadName.of(template, thread.countryName)
                        if (current.archived || current.name == name) return@mapNotNull null
                        EmbassyThreadRename(thread.threadId, current.parentChannelId, name, thread.countryName)
                    }
            if (renames.isNotEmpty()) eventPublisher.publish(EmbassyThreadRenameEvent(renames, clock.millis()))
            auditLog.record(
                actor,
                AuditAction.EMBASSY_THREADS_RENAME_REQUESTED,
                "Botschafts-Threads nach der Vorlage $template umbenennen lassen: ${renames.size} Threads",
            )
            renames.size
        }!!

    /** Records each rename the bot could not do in the system log. */
    fun applyReport(report: EmbassyThreadRenameReport) =
        transactionTemplate.executeWithoutResult {
            report.failures.forEach { failure ->
                log.warn("Embassy thread {} of {} not renamed: {}", failure.threadId, failure.countryName, failure.failure)
                systemLog.recordProblem(
                    SystemProblem.EmbassyThreadRenameFailed(
                        threadName = failure.threadName ?: failure.threadId,
                        countryName = failure.countryName,
                        cause =
                            when (failure.failure) {
                                ThreadRenameFailure.MISSING -> EmbassyThreadCause.MISSING
                                ThreadRenameFailure.NO_PERMISSION -> EmbassyThreadCause.NO_PERMISSION
                                ThreadRenameFailure.FAILED -> EmbassyThreadCause.FAILED
                            },
                    ),
                )
            }
        }
}
