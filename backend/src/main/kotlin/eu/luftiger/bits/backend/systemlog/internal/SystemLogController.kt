package eu.luftiger.bits.backend.systemlog.internal

import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.backend.systemlog.domain.EntryKind
import eu.luftiger.bits.backend.systemlog.domain.ItemOutcome
import eu.luftiger.bits.backend.systemlog.domain.SyncStatus
import eu.luftiger.bits.backend.systemlog.domain.SystemLogEntry
import eu.luftiger.bits.backend.systemlog.domain.SystemLogItem
import kotlinx.serialization.Serializable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** The system log in the dashboard, read-only. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/system-log")
class SystemLogController(
    private val service: SystemLogService,
) {
    /**
     * Newest first, [SystemLogService.PAGE_SIZE] at a time; the next page starts [before] the last entry of this one.
     * [member] is a Discord ID or part of a name.
     */
    @GetMapping
    fun entries(
        @RequestParam(required = false) category: LogCategory?,
        @RequestParam(required = false) member: String?,
        @RequestParam(required = false) roleId: String?,
        @RequestParam(required = false) before: Long?,
    ): SystemLogPage = service.page(category, member, roleId, before)

    /** The role changes of a sync run, or the members an error is about; filtered like [entries]. */
    @GetMapping("/{id}/items")
    fun items(
        @PathVariable id: Long,
        @RequestParam(required = false) member: String?,
        @RequestParam(required = false) roleId: String?,
    ): List<SystemLogItemResponse> = service.items(id, member, roleId)

    /** The roles to filter by: every role that appears in the log. */
    @GetMapping("/roles")
    fun roles(): List<NamedRef> = service.roles()
}

@Serializable
data class SystemLogPage(
    val entries: List<SystemLogEntryResponse>,
    /** Pass as `before` for the next page; `null` on the last one. */
    val nextBefore: Long?,
)

@Serializable
data class SystemLogEntryResponse(
    val id: Long,
    /** Epoch milliseconds. */
    val occurredAt: Long,
    val type: LogEntryType,
    val category: LogCategory,
    /** Only for [LogEntryType.SYNC_RUN]. */
    val sync: SyncRunResponse?,
    val member: NamedRef?,
    /** The Discord account a WarEra account was linked to before, for [LogEntryType.ACCOUNT_RELINKED]. */
    val previousMember: NamedRef?,
    val wareraUsername: String?,
    val role: NamedRef?,
    /** German description of an error. */
    val message: String?,
    /** Whether [SystemLogController.items] has anything for this entry. */
    val hasItems: Boolean,
) {
    companion object {
        fun of(
            entry: SystemLogEntry,
            hasItems: Boolean,
            noResponseBefore: Instant,
        ) = SystemLogEntryResponse(
            id = requireNotNull(entry.id),
            occurredAt = entry.occurredAt.toEpochMilli(),
            type = LogEntryType.of(entry.kind),
            category = LogCategory.of(entry.kind.category),
            sync =
                if (entry.kind == EntryKind.SYNC_RUN) {
                    SyncRunResponse(
                        trigger = entry.syncTrigger?.let(SyncTriggerType::of),
                        status = SyncRunStatus.of(requireNotNull(entry.syncStatus), entry.occurredAt < noResponseBefore),
                        linkedCount = entry.linkedCount,
                        memberCount = entry.memberCount,
                        staleMemberCount = entry.staleMemberCount,
                        granted = entry.grantedCount,
                        removed = entry.removedCount,
                        skipped = entry.skippedCount,
                        failed = entry.failedCount,
                    )
                } else {
                    null
                },
            member = entry.discordUserId?.let { NamedRef(it, entry.discordName) },
            previousMember = entry.previousDiscordUserId?.let { NamedRef(it, entry.previousDiscordName) },
            wareraUsername = entry.wareraUsername,
            role = entry.roleId?.let { NamedRef(it, entry.roleName) },
            message = entry.message,
            hasItems = hasItems,
        )
    }
}

@Serializable
data class SyncRunResponse(
    /** `null` for a sync that was aborted before its trigger was known. */
    val trigger: SyncTriggerType?,
    val status: SyncRunStatus,
    /** Linked accounts when the sync started. */
    val linkedCount: Int?,
    /** Members the bot synced; the counts are `null` until the bot reports back. */
    val memberCount: Int?,
    val staleMemberCount: Int?,
    val granted: Int?,
    val removed: Int?,
    val skipped: Int?,
    val failed: Int?,
)

@Serializable
data class SystemLogItemResponse(
    val id: Long,
    val member: NamedRef,
    /** `null` for an item that is about the member only, e.g. missing WarEra data. */
    val role: NamedRef?,
    val outcome: ItemOutcomeType,
    val reason: String?,
) {
    companion object {
        fun of(item: SystemLogItem) =
            SystemLogItemResponse(
                id = requireNotNull(item.id),
                member = NamedRef(item.discordUserId, item.discordName),
                role = item.roleId?.let { NamedRef(it, item.roleName) },
                outcome = ItemOutcomeType.of(item.outcome),
                reason = item.reason,
            )
    }
}

/** A member or role by its Discord ID, with its name as last seen; `null` if never seen. */
@Serializable
data class NamedRef(
    val id: String,
    val name: String?,
)

/**
 * The API's names for [EntryKind], [SyncStatus] and the others; the dashboard spells them out in
 * `frontend/src/lib/api.ts`.
 */
@Serializable
enum class LogEntryType {
    SYNC_RUN,
    ACCOUNT_LINKED,
    ACCOUNT_RELINKED,
    WARERA_USERS_UNAVAILABLE,
    GOVERNMENT_UNAVAILABLE,
    ROLE_NOT_ASSIGNABLE,
    SYNC_ABORTED,
    EVENT_UNDELIVERED,
    OUTBOX_STUCK,
    ;

    companion object {
        fun of(kind: EntryKind) =
            when (kind) {
                EntryKind.SYNC_RUN -> SYNC_RUN
                EntryKind.ACCOUNT_LINKED -> ACCOUNT_LINKED
                EntryKind.ACCOUNT_RELINKED -> ACCOUNT_RELINKED
                EntryKind.WARERA_USERS_UNAVAILABLE -> WARERA_USERS_UNAVAILABLE
                EntryKind.GOVERNMENT_UNAVAILABLE -> GOVERNMENT_UNAVAILABLE
                EntryKind.ROLE_NOT_ASSIGNABLE -> ROLE_NOT_ASSIGNABLE
                EntryKind.SYNC_ABORTED -> SYNC_ABORTED
                EntryKind.EVENT_UNDELIVERED -> EVENT_UNDELIVERED
                EntryKind.OUTBOX_STUCK -> OUTBOX_STUCK
            }
    }
}

@Serializable
enum class LogCategory {
    SYNC,
    ACCOUNT,
    ERROR,
    ;

    val category: EntryKind.Category
        get() =
            when (this) {
                SYNC -> EntryKind.Category.SYNC
                ACCOUNT -> EntryKind.Category.ACCOUNT
                ERROR -> EntryKind.Category.ERROR
            }

    companion object {
        fun of(category: EntryKind.Category) =
            when (category) {
                EntryKind.Category.SYNC -> SYNC
                EntryKind.Category.ACCOUNT -> ACCOUNT
                EntryKind.Category.ERROR -> ERROR
            }
    }
}

@Serializable
enum class SyncTriggerType {
    HOURLY,
    RULE_CHANGE,
    ACCOUNT_CHANGE,
    ;

    companion object {
        fun of(trigger: SyncTrigger) =
            when (trigger) {
                SyncTrigger.HOURLY -> HOURLY
                SyncTrigger.RULE_CHANGE -> RULE_CHANGE
                SyncTrigger.ACCOUNT_CHANGE -> ACCOUNT_CHANGE
            }
    }
}

@Serializable
enum class SyncRunStatus {
    PENDING,
    APPLIED,
    STALE,
    NO_RULES,

    /** Still pending after [SystemLogService.NO_RESPONSE_AFTER]. */
    NO_RESPONSE,
    ;

    companion object {
        fun of(
            status: SyncStatus,
            overdue: Boolean,
        ) = when (status) {
            SyncStatus.PENDING -> if (overdue) NO_RESPONSE else PENDING
            SyncStatus.APPLIED -> APPLIED
            SyncStatus.STALE -> STALE
            SyncStatus.NO_RULES -> NO_RULES
        }
    }
}

@Serializable
enum class ItemOutcomeType {
    GRANTED,
    REMOVED,
    SKIPPED,
    FAILED,
    ;

    companion object {
        fun of(outcome: ItemOutcome) =
            when (outcome) {
                ItemOutcome.GRANTED -> GRANTED
                ItemOutcome.REMOVED -> REMOVED
                ItemOutcome.SKIPPED -> SKIPPED
                ItemOutcome.FAILED -> FAILED
            }
    }
}
