package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import eu.luftiger.bits.backend.account.api.AccountUnlinked
import eu.luftiger.bits.backend.account.api.NicknameOptedIn
import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggled
import eu.luftiger.bits.backend.feature.api.FeatureToggles
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncService.FormerAccount
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
 * rule change, for a member who opts in to their WarEra name as nickname, and every hour with fresh WarEra data.
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
 *
 * While both the role sync and nicknames are switched off, requests are dropped and the hourly refresh does not run.
 * Switching one of them on starts a refresh, which catches up on the account changes missed meanwhile.
 */
@Component
class RoleSyncTrigger(
    private val syncService: RoleSyncService,
    private val systemLog: SystemLog,
    private val features: FeatureToggles,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val syncs = Executors.newSingleThreadExecutor { Thread(it, "role-sync") }
    private val refreshes = Executors.newSingleThreadExecutor { Thread(it, "role-sync-refresh") }
    private val refreshing = AtomicBoolean()

    private val lock = Any()
    private var syncAllPending = false
    private val pendingConfirmedRoleIds = HashSet<String>()
    private val pendingMembers = LinkedHashSet<String>()
    private val pendingFormerAccounts = HashMap<String, FormerAccount>()
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

    /** The previous Discord account of a relinked WarEra account loses its roles and the nickname from it. */
    @ApplicationModuleListener
    fun onAccountLinked(event: AccountLinked) =
        request {
            pendingMembers += event.discordUserId
            event.previousDiscordUserId?.let { previous ->
                pendingMembers += previous
                pendingFormerAccounts[previous] = FormerAccount(event.wareraUserId, event.wareraUsername)
            }
        }

    /** An unlinked account meets no rule, so the member loses the managed roles and the nickname from WarEra. */
    @ApplicationModuleListener
    fun onAccountUnlinked(event: AccountUnlinked) =
        request {
            pendingMembers += event.discordUserId
            event.wareraUserId?.let { pendingFormerAccounts[event.discordUserId] = FormerAccount(it, event.wareraUsername) }
        }

    @ApplicationModuleListener
    fun onNicknameOptedIn(event: NicknameOptedIn) = request { pendingMembers += event.discordUserId }

    @ApplicationModuleListener
    fun onFeatureToggled(event: FeatureToggled) {
        if (event.enabled && event.feature in SYNCED_FEATURES) hourly()
    }

    @Scheduled(cron = "\${bits.role-sync.cron}")
    fun hourly() {
        if (!active()) return
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

    private fun active() = SYNCED_FEATURES.any(features::isEnabled)

    private fun request(add: () -> Unit) {
        if (!active()) return
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
            val pending =
                synchronized(lock) {
                    if (!syncAllPending && pendingMembers.isEmpty()) {
                        draining = false
                        return
                    }
                    Pending(
                        syncAll = syncAllPending,
                        confirmedRoleIds = pendingConfirmedRoleIds.toList(),
                        members = pendingMembers.toList(),
                        formerAccounts = pendingFormerAccounts.toMap(),
                    ).also {
                        syncAllPending = false
                        pendingConfirmedRoleIds.clear()
                        pendingMembers.clear()
                        pendingFormerAccounts.clear()
                    }
                }
            // Members first: a single lookup each, while the sync of everybody may have to load users it has not seen.
            if (pending.members.isNotEmpty()) {
                attempt(SyncTrigger.ACCOUNT_CHANGE) { syncService.syncMembers(pending.members, pending.formerAccounts) }
            }
            if (pending.syncAll) {
                // Only rule changes confirm roles, see syncAllWithCachedData.
                val trigger = if (pending.confirmedRoleIds.isEmpty()) SyncTrigger.HOURLY else SyncTrigger.RULE_CHANGE
                attempt(trigger) { syncService.syncAll(pending.confirmedRoleIds, trigger) }
            }
        }
    }

    private class Pending(
        val syncAll: Boolean,
        val confirmedRoleIds: List<String>,
        val members: List<String>,
        val formerAccounts: Map<String, FormerAccount>,
    )

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

    private companion object {
        /** What a sync sets, see [RoleSyncService]. */
        val SYNCED_FEATURES = listOf(BitsFeature.ROLE_SYNC, BitsFeature.NICKNAMES)
    }
}
