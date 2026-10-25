package eu.luftiger.bits.backend.systemlog.internal

import eu.luftiger.bits.backend.systemlog.api.SystemLogRecords
import eu.luftiger.bits.backend.systemlog.api.dto.MemberLogEntry
import eu.luftiger.bits.backend.systemlog.api.dto.MemberLogItem
import eu.luftiger.bits.backend.systemlog.api.dto.MemberLogRecords
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class SystemLogRecordsLookup(
    private val entries: SystemLogEntryRepository,
    private val items: SystemLogItemRepository,
) : SystemLogRecords {
    override fun lastSyncOfAll(): Instant? = entries.findLastAppliedSyncOfAll()

    /**
     * A relink names two members; each gets only their own side of it. Items belong to one member each, but the entries
     * they belong to may be about others, so only their time, kind and trigger are taken.
     */
    @Transactional(readOnly = true)
    override fun recordsAbout(discordUserId: String): MemberLogRecords {
        val ownItems = items.findByDiscordUserIdOrderById(discordUserId)
        val itemEntries = entries.findAllById(ownItems.map { it.entryId }.distinct()).associateBy { it.id }
        return MemberLogRecords(
            entries =
                entries.findAboutMember(discordUserId).map { entry ->
                    val previous = entry.discordUserId != discordUserId
                    MemberLogEntry(
                        occurredAt = entry.occurredAt,
                        kind = entry.kind.name,
                        discordName = if (previous) entry.previousDiscordName else entry.discordName,
                        wareraUserId = entry.wareraUserId,
                        wareraUsername = entry.wareraUsername,
                        previousAccount = previous,
                    )
                },
            items =
                ownItems.mapNotNull { item ->
                    val entry = itemEntries[item.entryId] ?: return@mapNotNull null
                    MemberLogItem(
                        occurredAt = entry.occurredAt,
                        entryKind = entry.kind.name,
                        syncTrigger = entry.syncTrigger?.name,
                        discordName = item.discordName,
                        roleId = item.roleId,
                        roleName = item.roleName,
                        outcome = item.outcome.name,
                        reason = item.reason,
                    )
                },
        )
    }
}
