package eu.luftiger.bits.backend.systemlog.internal

import eu.luftiger.bits.backend.systemlog.api.AccountLinkRecord
import eu.luftiger.bits.backend.systemlog.api.AccountRemovalRecord
import eu.luftiger.bits.backend.systemlog.api.EmbassyChangeOutcome
import eu.luftiger.bits.backend.systemlog.api.EmbassySyncResult
import eu.luftiger.bits.backend.systemlog.api.EmbassyThreadCause
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.backend.systemlog.domain.EntryKind
import eu.luftiger.bits.backend.systemlog.domain.ItemOutcome
import eu.luftiger.bits.backend.systemlog.domain.SyncStatus
import eu.luftiger.bits.backend.systemlog.domain.SystemLogEntry
import eu.luftiger.bits.backend.systemlog.domain.SystemLogItem
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.NicknameChangeOutcome
import eu.luftiger.bits.shared.ReportedNicknameChange
import eu.luftiger.bits.shared.RoleChangeOutcome
import eu.luftiger.bits.shared.RoleSyncReport
import eu.luftiger.bits.shared.UndeliveredEventReport
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration

@Service
class SystemLogService(
    private val entries: SystemLogEntryRepository,
    private val items: SystemLogItemRepository,
    private val clock: Clock,
) : SystemLog {
    @Transactional
    override fun recordSyncRun(
        trigger: SyncTrigger,
        linkedCount: Int,
    ): Long =
        requireNotNull(
            entries
                .save(
                    SystemLogEntry(
                        clock.instant(),
                        EntryKind.SYNC_RUN,
                        syncTrigger = trigger,
                        syncStatus = SyncStatus.PENDING,
                        linkedCount = linkedCount,
                    ),
                ).id,
        )

    @Transactional
    override fun recordSyncWithoutRules(trigger: SyncTrigger) {
        entries.save(SystemLogEntry(clock.instant(), EntryKind.SYNC_RUN, syncTrigger = trigger, syncStatus = SyncStatus.NO_RULES))
    }

    @Transactional
    override fun recordEmbassySyncRun(
        trigger: SyncTrigger,
        threadCount: Int,
    ): Long =
        requireNotNull(
            entries
                .save(
                    SystemLogEntry(
                        clock.instant(),
                        EntryKind.EMBASSY_SYNC_RUN,
                        syncTrigger = trigger,
                        syncStatus = SyncStatus.PENDING,
                        linkedCount = threadCount,
                    ),
                ).id,
        )

    /** The counts reuse those of the role sync: thread additions count as granted, thread removals as removed. */
    @Transactional
    override fun recordEmbassySyncResult(
        syncId: Long,
        result: EmbassySyncResult,
    ): Boolean {
        val entry =
            entries.findByIdOrNull(syncId)?.takeIf { it.kind == EntryKind.EMBASSY_SYNC_RUN }
                ?: throw NoSuchElementException("No embassy sync $syncId")
        if (entry.reportedAt != null) return false
        items.saveAll(
            result.changes.map {
                SystemLogItem(syncId, it.discordUserId, it.memberName, it.roleId, it.roleName, outcome(it.outcome), it.reason)
            },
        )
        val counts = result.changes.groupingBy { it.outcome }.eachCount()
        entry.memberCount = result.syncedThreadCount
        entry.staleMemberCount = 0
        entry.grantedCount = (counts[EmbassyChangeOutcome.THREAD_ADDED] ?: 0) + (counts[EmbassyChangeOutcome.ROLE_GRANTED] ?: 0)
        entry.removedCount = (counts[EmbassyChangeOutcome.THREAD_REMOVED] ?: 0) + (counts[EmbassyChangeOutcome.ROLE_REMOVED] ?: 0)
        entry.skippedCount = counts[EmbassyChangeOutcome.SKIPPED] ?: 0
        entry.failedCount = counts[EmbassyChangeOutcome.FAILED] ?: 0
        entry.syncStatus = SyncStatus.APPLIED
        entry.reportedAt = clock.instant()
        return true
    }

    @Transactional
    override fun recordAccountLinked(link: AccountLinkRecord) {
        val previous = link.previousDiscordUserId
        entries.save(
            SystemLogEntry(
                clock.instant(),
                if (previous == null) EntryKind.ACCOUNT_LINKED else EntryKind.ACCOUNT_RELINKED,
                discordUserId = link.discordUserId,
                discordName = link.discordName ?: latestNames(listOf(link.discordUserId))[link.discordUserId],
                previousDiscordUserId = previous,
                previousDiscordName = previous?.let { latestNames(listOf(it))[it] },
                wareraUserId = link.wareraUserId,
                wareraUsername = link.wareraUsername,
            ),
        )
    }

    @Transactional
    override fun recordAccountRemoved(removal: AccountRemovalRecord) {
        entries.save(
            SystemLogEntry(
                clock.instant(),
                EntryKind.ACCOUNT_REMOVED,
                discordUserId = removal.discordUserId,
                discordName = removal.discordName ?: latestNames(listOf(removal.discordUserId))[removal.discordUserId],
                wareraUserId = removal.wareraUserId,
                wareraUsername = removal.wareraUsername,
            ),
        )
    }

    @Transactional
    override fun recordProblem(problem: SystemProblem) {
        val now = clock.instant()
        when (problem) {
            is SystemProblem.WareraUsersUnavailable -> {
                val ids = problem.discordUserIds.distinct()
                val entry =
                    entries.save(
                        SystemLogEntry(
                            now,
                            EntryKind.WARERA_USERS_UNAVAILABLE,
                            message =
                                "Die WarEra-Daten von ${members(ids.size)} konnten nicht geladen werden. Ihre Rollen bleiben, " +
                                    "wie sie sind.",
                        ),
                    )
                val names = latestNames(ids)
                items.saveAll(
                    ids.map { id ->
                        SystemLogItem(requireNotNull(entry.id), id, names[id], null, null, ItemOutcome.SKIPPED, "WarEra-Daten nicht ladbar")
                    },
                )
            }
            is SystemProblem.GovernmentUnavailable ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.GOVERNMENT_UNAVAILABLE,
                        message =
                            "Die Regierung von ${problem.countryName ?: problem.countryId} konnte nicht geladen werden. " +
                                "Regeln für Regierungsämter dieses Landes wurden übersprungen.",
                    ),
                )
            is SystemProblem.PartiesUnavailable ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.PARTIES_UNAVAILABLE,
                        message =
                            "Die Parteien eines Landes konnten nicht geladen werden. Regeln für " +
                                "${problem.partyNames.joinToString(", ")} wurden übersprungen.",
                    ),
                )
            is SystemProblem.MilitaryUnitUnavailable ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.MILITARY_UNIT_UNAVAILABLE,
                        message =
                            "Die MU ${problem.muName ?: problem.muId} konnte nicht geladen werden. " +
                                "Regeln für die Führung dieser MU wurden übersprungen.",
                    ),
                )
            is SystemProblem.EmbassyGovernmentUnavailable ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.GOVERNMENT_UNAVAILABLE,
                        message =
                            "Die Regierung von ${problem.countryName} konnte nicht geladen werden. Aus ihrem Botschafts-Thread " +
                                "wurde niemand wegen des Amts entfernt.",
                    ),
                )
            is SystemProblem.EmbassyThreadUnavailable ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.EMBASSY_THREAD_UNAVAILABLE,
                        message =
                            "Der Botschafts-Thread ${problem.threadName} (${problem.countryName}) wurde nicht abgeglichen: " +
                                when (problem.cause) {
                                    EmbassyThreadCause.MISSING -> "Er wurde gelöscht, oder der Bot sieht ihn nicht."
                                    EmbassyThreadCause.NO_PERMISSION ->
                                        "Dem Bot fehlen im Kanal die Rechte „Threads verwalten“ oder „Nachrichten in Threads senden“."
                                    EmbassyThreadCause.FAILED -> "Discord hat die Anfrage abgelehnt."
                                },
                    ),
                )
            is SystemProblem.EmbassyThreadCreationFailed ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.EMBASSY_THREAD_CREATION_FAILED,
                        message =
                            "Der Botschafts-Thread ${problem.threadName} (${problem.countryName}) wurde nicht angelegt: " +
                                problem.reason,
                    ),
                )
            is SystemProblem.EmbassyThreadRenameFailed ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.EMBASSY_THREAD_RENAME_FAILED,
                        message =
                            "Der Botschafts-Thread ${problem.threadName} (${problem.countryName}) wurde nicht umbenannt: " +
                                when (problem.cause) {
                                    EmbassyThreadCause.MISSING -> "Er wurde gelöscht, oder der Bot sieht ihn nicht."
                                    EmbassyThreadCause.NO_PERMISSION -> "Dem Bot fehlt im Kanal das Recht „Threads verwalten“."
                                    EmbassyThreadCause.FAILED -> "Discord hat die Anfrage abgelehnt."
                                },
                    ),
                )
            is SystemProblem.RoleNotAssignable ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.ROLE_NOT_ASSIGNABLE,
                        roleId = problem.roleId,
                        roleName = problem.roleName,
                        message =
                            if (problem.roleName == null) {
                                "Die Rolle wurde in Discord gelöscht. Ihre Regeln laufen nicht mehr."
                            } else {
                                "Der Bot kann die Rolle nicht vergeben, z. B. weil sie über seiner Rolle steht. Ihre Regeln " +
                                    "laufen nicht mehr."
                            },
                    ),
                )
            is SystemProblem.SyncAborted ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.SYNC_ABORTED,
                        syncTrigger = problem.trigger,
                        message = "Der Sync ist abgebrochen: ${problem.error.take(MAX_ERROR_LENGTH)}",
                    ),
                )
            is SystemProblem.VoteEvaluationDelayed ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.VOTE_EVALUATION_DELAYED,
                        message =
                            "Die Auswertung der Abstimmung „${problem.question}“ ist fehlgeschlagen und wird alle paar Minuten " +
                                "wiederholt: ${problem.error.take(MAX_ERROR_LENGTH)}",
                    ),
                )
            is SystemProblem.OutboxStuck ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.OUTBOX_STUCK,
                        message =
                            "${problem.count} Events für den Bot warten seit über 10 Minuten auf die Zustellung. Redis ist " +
                                "vermutlich nicht erreichbar; sie werden zugestellt, sobald es wieder geht.",
                    ),
                )
            is SystemProblem.MemberReportRefused ->
                entries.save(
                    SystemLogEntry(
                        now,
                        EntryKind.MEMBER_REPORT_REFUSED,
                        message =
                            "Laut der Mitgliederliste des Bots hätten ${problem.departedCount} von ${problem.linkedCount} " +
                                "verknüpften Mitgliedern den Server verlassen. Das sieht nach einer fehlerhaften Liste aus, " +
                                "daher wurde keine Verknüpfung gelöscht.",
                    ),
                )
        }
    }

    /**
     * Stores what the bot changed for the sync [syncId]. A report that comes again, e.g. because the answer to the first
     * got lost, changes nothing.
     *
     * @throws ResponseStatusException 404 if there is no such sync run, e.g. because it was deleted already
     */
    @Transactional
    fun applyReport(
        syncId: Long,
        report: RoleSyncReport,
    ) {
        val entry =
            entries.findByIdOrNull(syncId)?.takeIf { it.kind == EntryKind.SYNC_RUN }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (entry.reportedAt != null) return
        items.saveAll(
            report.changes.map {
                SystemLogItem(syncId, it.discordUserId, it.memberName, it.roleId, it.roleName, outcome(it.outcome), it.reason)
            } +
                report.nicknameChanges.map {
                    SystemLogItem(syncId, it.discordUserId, it.memberName, null, null, outcome(it.outcome), nicknameReason(it))
                },
        )
        val counts = report.changes.groupingBy { it.outcome }.eachCount()
        val nicknameCounts = report.nicknameChanges.groupingBy { it.outcome }.eachCount()
        entry.memberCount = report.memberCount
        entry.staleMemberCount = report.staleMemberCount
        entry.grantedCount = counts[RoleChangeOutcome.GRANTED] ?: 0
        entry.removedCount = counts[RoleChangeOutcome.REMOVED] ?: 0
        entry.skippedCount = (counts[RoleChangeOutcome.SKIPPED] ?: 0) + (nicknameCounts[NicknameChangeOutcome.SKIPPED] ?: 0)
        entry.failedCount = (counts[RoleChangeOutcome.FAILED] ?: 0) + (nicknameCounts[NicknameChangeOutcome.FAILED] ?: 0)
        entry.nicknameCount = (nicknameCounts[NicknameChangeOutcome.SET] ?: 0) + (nicknameCounts[NicknameChangeOutcome.RESET] ?: 0)
        entry.syncStatus = if (report.memberCount == 0 && report.staleMemberCount > 0) SyncStatus.STALE else SyncStatus.APPLIED
        entry.reportedAt = clock.instant()
    }

    @Transactional
    fun recordUndelivered(report: UndeliveredEventReport) {
        entries.save(
            SystemLogEntry(
                clock.instant(),
                EntryKind.EVENT_UNDELIVERED,
                message =
                    "Der Bot hat das Event ${report.eventType ?: "(unlesbar)"} ${report.streamId} nach ${report.deliveries} " +
                        "Zustellversuchen aufgegeben. Es liegt in ${EventStream.DEAD_LETTER_KEY}.",
            ),
        )
    }

    fun page(
        category: LogCategory?,
        member: String?,
        roleId: String?,
        before: Long?,
    ): SystemLogPage {
        val kinds = EntryKind.entries.filter { category == null || it.category == category.category }.map { it.name }
        // One more than a page tells whether another page follows.
        val found = entries.findPage(kinds, member?.trim()?.ifEmpty { null }, roleId, before, PAGE_SIZE + 1)
        val page = found.take(PAGE_SIZE)
        val withItems = if (page.isEmpty()) emptySet() else items.findEntryIdsWithItems(page.map { requireNotNull(it.id) }).toSet()
        val noResponseBefore = clock.instant() - NO_RESPONSE_AFTER
        return SystemLogPage(
            entries = page.map { SystemLogEntryResponse.of(it, it.id in withItems, noResponseBefore) },
            nextBefore = if (found.size > PAGE_SIZE) page.last().id else null,
        )
    }

    fun items(
        entryId: Long,
        member: String?,
        roleId: String?,
    ): List<SystemLogItemResponse> {
        if (!entries.existsById(entryId)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return items.findByEntry(entryId, member?.trim()?.ifEmpty { null }, roleId).map(SystemLogItemResponse::of)
    }

    fun roles(): List<NamedRef> = entries.findRoles().map { NamedRef(it.id, it.name) }.sortedBy { (it.name ?: it.id).lowercase() }

    @Transactional
    fun deleteOlderThan(retention: Duration): Int = entries.deleteOccurredBefore(clock.instant() - retention)

    private fun latestNames(discordUserIds: Collection<String>): Map<String, String> =
        if (discordUserIds.isEmpty()) {
            emptyMap()
        } else {
            entries.findLatestNames(discordUserIds).mapNotNull { row -> row.name?.let { row.id to it } }.toMap()
        }

    private fun members(count: Int) = if (count == 1) "einem Mitglied" else "$count Mitgliedern"

    private fun outcome(outcome: RoleChangeOutcome) =
        when (outcome) {
            RoleChangeOutcome.GRANTED -> ItemOutcome.GRANTED
            RoleChangeOutcome.REMOVED -> ItemOutcome.REMOVED
            RoleChangeOutcome.SKIPPED -> ItemOutcome.SKIPPED
            RoleChangeOutcome.FAILED -> ItemOutcome.FAILED
        }

    private fun outcome(outcome: EmbassyChangeOutcome) =
        when (outcome) {
            EmbassyChangeOutcome.THREAD_ADDED -> ItemOutcome.THREAD_ADDED
            EmbassyChangeOutcome.THREAD_REMOVED -> ItemOutcome.THREAD_REMOVED
            EmbassyChangeOutcome.ROLE_GRANTED -> ItemOutcome.GRANTED
            EmbassyChangeOutcome.ROLE_REMOVED -> ItemOutcome.REMOVED
            EmbassyChangeOutcome.SKIPPED -> ItemOutcome.SKIPPED
            EmbassyChangeOutcome.FAILED -> ItemOutcome.FAILED
        }

    private fun outcome(outcome: NicknameChangeOutcome) =
        when (outcome) {
            NicknameChangeOutcome.SET -> ItemOutcome.NICKNAME_SET
            NicknameChangeOutcome.RESET -> ItemOutcome.NICKNAME_RESET
            NicknameChangeOutcome.SKIPPED -> ItemOutcome.SKIPPED
            NicknameChangeOutcome.FAILED -> ItemOutcome.FAILED
        }

    /** Items of nicknames have no role, so the reason says what the item is about. */
    private fun nicknameReason(change: ReportedNicknameChange): String =
        when (change.outcome) {
            NicknameChangeOutcome.SET -> "Nickname: ${change.nickname}"
            NicknameChangeOutcome.RESET -> "Nickname aus WarEra entfernt"
            NicknameChangeOutcome.SKIPPED, NicknameChangeOutcome.FAILED ->
                listOfNotNull("Nickname ${change.nickname ?: "entfernen"}", change.reason).joinToString(": ")
        }

    companion object {
        const val PAGE_SIZE = 50

        /** A sync the bot has not reported back on after this long shows as "Keine Rückmeldung vom Bot". */
        val NO_RESPONSE_AFTER: Duration = Duration.ofMinutes(15)

        private const val MAX_ERROR_LENGTH = 500
    }
}
