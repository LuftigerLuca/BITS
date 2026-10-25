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

    fun recordAccountLinked(link: AccountLinkRecord)

    fun recordAccountRemoved(removal: AccountRemovalRecord)

    fun recordProblem(problem: SystemProblem)
}
