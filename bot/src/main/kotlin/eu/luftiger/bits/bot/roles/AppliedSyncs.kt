package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.RoleSyncEvent

/**
 * Remembers when the target state of each member was computed, so that a sync delivered late (pending after a
 * restart, retried after a failure) does not undo a newer one. Kept in memory only: after a restart every sync
 * applies, and the next full sync corrects whatever an old one got wrong.
 *
 * Not thread-safe; the event consumer handles one event at a time.
 */
class AppliedSyncs {
    private var fullSyncAt = Long.MIN_VALUE
    private val memberSyncAt = HashMap<String, Long>()

    /** Whether [event] is older than the sync last applied to the member. */
    fun isStale(
        discordUserId: String,
        event: RoleSyncEvent,
    ): Boolean = event.occurredAt < maxOf(fullSyncAt, memberSyncAt[discordUserId] ?: Long.MIN_VALUE)

    fun record(event: RoleSyncEvent) {
        val discordUserIds = event.discordUserIds
        if (discordUserIds == null) {
            if (event.occurredAt <= fullSyncAt) return
            fullSyncAt = event.occurredAt
            // Older member syncs no longer matter.
            memberSyncAt.values.removeIf { it <= fullSyncAt }
        } else {
            discordUserIds.forEach { memberSyncAt.merge(it, event.occurredAt, ::maxOf) }
        }
    }
}
