package eu.luftiger.bits.backend.systemlog.api

import org.springframework.modulith.NamedInterface

/**
 * Cross-module boundary of the system log: what BITS does on its own, for admins in the dashboard. Each call joins the
 * caller's transaction, if there is one, so an entry is only kept if what it describes is.
 */
@NamedInterface("api")
interface SystemLog {
    /**
     * Records a sync run that is about to publish its event, for [linkedCount] linked accounts. Returns the ID that goes
     * into the event as `syncId`, under which the bot reports what it changed.
     */
    fun recordSyncRun(
        trigger: SyncTrigger,
        linkedCount: Int,
    ): Long

    /** Records a sync run that publishes nothing, since no rule can be run. */
    fun recordSyncWithoutRules(trigger: SyncTrigger)

    /**
     * Records an embassy sync of [threadCount] threads that is about to publish its event. Returns the ID that goes into
     * the event as `syncId`, under which the bot reports what it changed.
     */
    fun recordEmbassySyncRun(
        trigger: SyncTrigger,
        threadCount: Int,
    ): Long

    /**
     * Stores what the bot changed for the embassy sync [syncId]. Returns `false` if it was stored already, e.g. because
     * the bot sent the report again, and changes nothing then.
     *
     * @throws NoSuchElementException if there is no such embassy sync, e.g. because it was deleted already
     */
    fun recordEmbassySyncResult(
        syncId: Long,
        result: EmbassySyncResult,
    ): Boolean

    fun recordAccountLinked(link: AccountLinkRecord)

    fun recordAccountRemoved(removal: AccountRemovalRecord)

    fun recordProblem(problem: SystemProblem)
}
