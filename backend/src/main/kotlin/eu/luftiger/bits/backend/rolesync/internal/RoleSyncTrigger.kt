package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import eu.luftiger.bits.backend.account.api.AccountUnlinked
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts syncs in the background: right away for a member who links or unlinks an account, for everybody after a
 * rule change, and every hour with fresh WarEra data.
 *
 * Two lanes keep the quick syncs from waiting behind the hourly one. The hourly refresh loads every linked user, about
 * one per second, on a lane of its own and publishes nothing; once done, it asks for a sync of all members with the
 * data it loaded. Every sync that publishes runs on the other lane, one at a time, so a sync never overwrites a newer
 * one with data it read earlier.
 *
 * Requests that arrive while the lane is busy are merged: at most one sync of all members and one sync of the members
 * asked for since wait, however many requests came in. A refresh that is still running when the next hour starts is
 * not repeated. One refresh takes about a second per linked user, so the hourly schedule holds up to roughly 3000
 * links; beyond that, refreshes are skipped and run every other hour.
 *
 * A sync that fails is recorded in the [SystemLog]; a merged sync of all members counts as a rule change if one of the
 * requests was.
 */
@Component
class RoleSyncTrigger(
    private val syncService: RoleSyncService,
    private val systemLog: SystemLog,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val syncs = Executors.newSingleThreadExecutor { Thread(it, "role-sync") }
    private val refreshes = Executors.newSingleThreadExecutor { Thread(it, "role-sync-refresh") }
    private val refreshing = AtomicBoolean()

    private val lock = Any()
    private var syncAllPending = false
    private val pendingConfirmedRoleIds = HashSet<String>()
    private val pendingMembers = LinkedHashSet<String>()
    private var draining = false

    /**
     * Syncs all members with the WarEra data loaded last. [confirmedRoleIds] are roles whose rules an admin just
     * changed, see [eu.luftiger.bits.shared.RoleSyncEvent.confirmedRoleIds].
     */
    fun syncAllWithCachedData(confirmedRoleIds: Collection<String> = emptyList()) =
        request {
            syncAllPending = true
            pendingConfirmedRoleIds += confirmedRoleIds
        }

    /** The previous Discord account of a relinked WarEra account loses its roles. */
    @ApplicationModuleListener
    fun onAccountLinked(event: AccountLinked) =
        request { pendingMembers += listOfNotNull(event.discordUserId, event.previousDiscordUserId) }

    /** An unlinked account meets no rule, so the member loses the managed roles. */
    @ApplicationModuleListener
    fun onAccountUnlinked(event: AccountUnlinked) = request { pendingMembers += event.discordUserId }

    @Scheduled(cron = "\${bits.role-sync.cron}")
    fun hourly() {
        if (!refreshing.compareAndSet(false, true)) {
            log.warn("Role sync refresh still running, skipping this one")
            return
        }
        refreshes.execute {
            try {
                syncService.refreshUsers()
                syncAllWithCachedData()
            } catch (e: Exception) {
                log.error("Role sync refresh failed", e)
            } finally {
                refreshing.set(false)
            }
        }
    }

    /** Waits until all syncs started so far have finished, so that tests do not see syncs of the previous test. */
    fun awaitIdle(timeout: Duration = Duration.ofSeconds(10)) {
        // A refresh ends by asking for a sync, so the refresh lane goes first.
        refreshes.submit {}.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        syncs.submit {}.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun request(add: () -> Unit) {
        synchronized(lock) {
            add()
            if (draining) return
            draining = true
        }
        syncs.execute(::drain)
    }

    /** Runs what is pending until nothing is left; requests that come in meanwhile are picked up by the next round. */
    private fun drain() {
        while (true) {
            val (syncAll, confirmedRoleIds, members) =
                synchronized(lock) {
                    if (!syncAllPending && pendingMembers.isEmpty()) {
                        draining = false
                        return
                    }
                    Triple(syncAllPending, pendingConfirmedRoleIds.toList(), pendingMembers.toList()).also {
                        syncAllPending = false
                        pendingConfirmedRoleIds.clear()
                        pendingMembers.clear()
                    }
                }
            // Members first: a single lookup each, while the sync of everybody may have to load users it has not seen.
            if (members.isNotEmpty()) attempt(SyncTrigger.ACCOUNT_CHANGE) { syncService.syncMembers(members) }
            if (syncAll) {
                // Only rule changes confirm roles, see syncAllWithCachedData.
                val trigger = if (confirmedRoleIds.isEmpty()) SyncTrigger.HOURLY else SyncTrigger.RULE_CHANGE
                attempt(trigger) { syncService.syncAll(confirmedRoleIds, trigger) }
            }
        }
    }

    private fun attempt(
        trigger: SyncTrigger,
        sync: () -> Unit,
    ) {
        try {
            sync()
        } catch (e: Exception) {
            log.error("Role sync failed", e)
            try {
                systemLog.recordProblem(SystemProblem.SyncAborted(trigger, e.message ?: e::class.simpleName.orEmpty()))
            } catch (logFailure: Exception) {
                // Likely the same cause, e.g. the database is gone.
                log.warn("Could not record the failed role sync in the system log", logFailure)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        refreshes.shutdownNow()
        syncs.shutdownNow()
    }
}
