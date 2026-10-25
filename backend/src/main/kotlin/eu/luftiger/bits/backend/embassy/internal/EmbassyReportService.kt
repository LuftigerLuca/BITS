package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.embassy.domain.DiscordThread
import eu.luftiger.bits.backend.embassy.domain.EmbassyThreadMember
import eu.luftiger.bits.backend.systemlog.api.EmbassyChange
import eu.luftiger.bits.backend.systemlog.api.EmbassyChangeOutcome
import eu.luftiger.bits.backend.systemlog.api.EmbassySyncResult
import eu.luftiger.bits.backend.systemlog.api.EmbassyThreadCause
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.shared.DiscordThreadDto
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadStatus
import eu.luftiger.bits.shared.ReportedEmbassyChange
import eu.luftiger.bits.shared.RoleChangeOutcome
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** What the bot reports: the private threads of the guild, and what it did with an embassy sync. */
@Service
class EmbassyReportService(
    private val discordThreadRepository: DiscordThreadRepository,
    private val threadRepository: EmbassyThreadRepository,
    private val threadMemberRepository: EmbassyThreadMemberRepository,
    private val systemLog: SystemLog,
) {
    @Transactional
    fun replaceThreads(threads: List<DiscordThreadDto>) {
        discordThreadRepository.deleteAllInBatch()
        discordThreadRepository.saveAll(
            threads.map {
                DiscordThread(
                    it.id,
                    it.name,
                    it.parentChannelId,
                    it.parentChannelName,
                    it.archived,
                    it.manageable,
                    it.everyoneCanView,
                    it.viewerRoleIds.toSet(),
                )
            },
        )
    }

    /**
     * Keeps who is in each synced thread for the dashboard, and the changes and threads the bot could not sync in the
     * system log. A report that comes again changes nothing; one for a thread that was unassigned meanwhile is dropped.
     *
     * @throws ResponseStatusException 404 if there is no such sync, e.g. because it was deleted already
     */
    @Transactional
    fun applyReport(
        syncId: Long,
        report: EmbassySyncReport,
    ) {
        val result =
            EmbassySyncResult(
                syncedThreadCount = report.threads.count { it.status == EmbassyThreadStatus.SYNCED },
                changes = report.changes.map(::change),
            )
        val first =
            try {
                systemLog.recordEmbassySyncResult(syncId, result)
            } catch (e: NoSuchElementException) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND)
            }
        if (!first) return
        val assigned = threadRepository.findAllById(report.threads.map { it.threadId }).associateBy { it.threadId }
        report.threads.forEach { reported ->
            val thread = assigned[reported.threadId] ?: return@forEach
            when (reported.status) {
                EmbassyThreadStatus.SYNCED -> {
                    threadMemberRepository.deleteByThreadId(thread.threadId)
                    threadMemberRepository.saveAll(
                        reported.members.distinctBy { it.discordUserId }.map {
                            EmbassyThreadMember(thread.threadId, it.discordUserId, it.name, it.reason.take(MAX_REASON_LENGTH))
                        },
                    )
                }
                EmbassyThreadStatus.MISSING, EmbassyThreadStatus.NO_PERMISSION, EmbassyThreadStatus.FAILED ->
                    systemLog.recordProblem(
                        SystemProblem.EmbassyThreadUnavailable(
                            reported.threadName ?: thread.threadName,
                            thread.countryName,
                            when (reported.status) {
                                EmbassyThreadStatus.MISSING -> EmbassyThreadCause.MISSING
                                EmbassyThreadStatus.NO_PERMISSION -> EmbassyThreadCause.NO_PERMISSION
                                else -> EmbassyThreadCause.FAILED
                            },
                        ),
                    )
            }
        }
    }

    /** Thread changes name the thread in the reason, since a system log item only has a column for a role. */
    private fun change(change: ReportedEmbassyChange): EmbassyChange {
        val thread = change.threadId != null
        val outcome =
            when (change.outcome) {
                RoleChangeOutcome.GRANTED -> if (thread) EmbassyChangeOutcome.THREAD_ADDED else EmbassyChangeOutcome.ROLE_GRANTED
                RoleChangeOutcome.REMOVED -> if (thread) EmbassyChangeOutcome.THREAD_REMOVED else EmbassyChangeOutcome.ROLE_REMOVED
                RoleChangeOutcome.SKIPPED -> EmbassyChangeOutcome.SKIPPED
                RoleChangeOutcome.FAILED -> EmbassyChangeOutcome.FAILED
            }
        val reason =
            if (thread) {
                listOfNotNull("Thread ${change.threadName ?: change.threadId}", change.reason).joinToString(": ")
            } else {
                change.reason
            }
        return EmbassyChange(change.discordUserId, change.memberName, change.roleId, change.roleName, outcome, reason)
    }

    private companion object {
        /** The column's length. */
        const val MAX_REASON_LENGTH = 255
    }
}
