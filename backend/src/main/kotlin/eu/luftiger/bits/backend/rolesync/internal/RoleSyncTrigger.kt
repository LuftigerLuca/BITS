package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import eu.luftiger.bits.backend.account.api.AccountUnlinked
import eu.luftiger.bits.backend.account.api.NicknameOptedIn
import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggled
import eu.luftiger.bits.backend.feature.api.FeatureToggles
import eu.luftiger.bits.backend.members.api.MemberJoined
import eu.luftiger.bits.backend.rolesync.internal.RoleSyncService.FormerAccount
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.backend.systemstatus.api.BackgroundJob
import eu.luftiger.bits.backend.systemstatus.api.BackgroundWork
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
 * Starts syncs in the background: right away for a member who links or unlinks an account or whom an admin asks for
 * in the dashboard, for everybody after a
 * rule change or a change of the status roles, for a member who opts in to their WarEra name as nickname, for a member
 * who joins while status roles are set, and every hour with fresh WarEra data.
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
 * While the role sync and nicknames are switched off and no status roles are given (see [StatusRoleLookup.active]),
 * requests are dropped and the hourly sync does not run. With only status roles, it syncs without loading WarEra data,
 * which they do not need. Switching one of them on starts a refresh, which catches up on the account changes missed
 * meanwhile.
 */
@Component
class RoleSyncTrigger(
    private val syncService: RoleSyncService,
    private val systemLog: SystemLog,
    private val features: FeatureToggles,
    private val statusRoles: StatusRoleLookup,
    private val work: BackgroundWork,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val syncs = Executors.newSingleThreadExecutor { Thread(it, "role-sync") }
    private val refreshes = Executors.newSingleThreadExecutor { Thread(it, "role-sync-refresh") }
    private val refreshing = AtomicBoolean()

    private val lock = Any()
    private var syncAllPending = false
    private var settingChangePending = false
    private val pendingConfirmedRoleIds = HashSet<String>()
    private val pendingRevokedRoleIds = HashSet<String>()
    private val pendingMembers = LinkedHashSet<String>()
    private var adminRequestPending = false
    private val pendingFormerAccounts = HashMap<String, FormerAccount>()
    private var draining = false

    /**
     * Syncs all members with the WarEra data loaded last. [confirmedRoleIds] are roles whose rules an admin just
     * changed, see [eu.luftiger.bits.shared.RoleSyncEvent.confirmedRoleIds]; [revokedRoleIds] are roles to take from
     * everybody once, see [RoleSyncService.syncAll].
     */
    fun syncAllWithCachedData(
        confirmedRoleIds: Collection<String> = emptyList(),
        revokedRoleIds: Collection<String> = emptyList(),
    ) = request {
        syncAllPending = true
        pendingConfirmedRoleIds += confirmedRoleIds
        pendingRevokedRoleIds += revokedRoleIds
    }

    /** Syncs all members after an admin changed the status roles. */
    fun syncAllAfterSettingChange() =
        request {
            syncAllPending = true
            settingChangePending = true
        }

    /**
     * Syncs [discordUserId] with fresh WarEra data, as an admin asked for in the dashboard. Returns `false` if the
     * request is dropped, since the role sync, nicknames and status roles are all switched off.
     */
    fun syncMemberOnRequest(discordUserId: String): Boolean {
        if (!active()) return false
        request {
            pendingMembers += discordUserId
            adminRequestPending = true
        }
        return true
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

    /** A new member gets the role for unverified members; without status roles, a sync would change nothing. */
    @ApplicationModuleListener
    fun onMemberJoined(event: MemberJoined) {
        if (statusRoles.active() != null) request { pendingMembers += event.discordUserId }
    }

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
        val withWareraData = WARERA_FEATURES.any(features::isEnabled)
        refreshes.execute {
            try {
                if (withWareraData) work.run(BackgroundJob.ROLE_SYNC_REFRESH) { syncService.refreshUsers() }
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

    private fun active() = WARERA_FEATURES.any(features::isEnabled) || statusRoles.active() != null

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
                        settingChange = settingChangePending,
                        confirmedRoleIds = pendingConfirmedRoleIds.toList(),
                        revokedRoleIds = pendingRevokedRoleIds.toList(),
                        members = pendingMembers.toList(),
                        formerAccounts = pendingFormerAccounts.toMap(),
                        adminRequest = adminRequestPending,
                    ).also {
                        syncAllPending = false
                        settingChangePending = false
                        pendingConfirmedRoleIds.clear()
                        pendingRevokedRoleIds.clear()
                        pendingMembers.clear()
                        adminRequestPending = false
                        pendingFormerAccounts.clear()
                    }
                }
            // Members first: a single lookup each, while the sync of everybody may have to load users it has not seen.
            if (pending.members.isNotEmpty()) {
                // Merged like the sync of all members: it counts as asked for by an admin if one of the requests was.
                val trigger = if (pending.adminRequest) SyncTrigger.ADMIN_REQUEST else SyncTrigger.ACCOUNT_CHANGE
                attempt(trigger) { syncService.syncMembers(pending.members, pending.formerAccounts, trigger) }
            }
            if (pending.syncAll) {
                // Only rule changes confirm roles, see syncAllWithCachedData.
                val trigger =
                    when {
                        pending.confirmedRoleIds.isNotEmpty() -> SyncTrigger.RULE_CHANGE
                        pending.settingChange -> SyncTrigger.SETTING_CHANGE
                        else -> SyncTrigger.HOURLY
                    }
                attempt(trigger) { syncService.syncAll(pending.confirmedRoleIds, trigger, pending.revokedRoleIds) }
            }
        }
    }

    private class Pending(
        val syncAll: Boolean,
        val settingChange: Boolean,
        val confirmedRoleIds: List<String>,
        val revokedRoleIds: List<String>,
        val members: List<String>,
        val formerAccounts: Map<String, FormerAccount>,
        val adminRequest: Boolean,
    )

    private fun attempt(
        trigger: SyncTrigger,
        sync: () -> Unit,
    ) {
        try {
            work.run(BackgroundJob.ROLE_SYNC, trigger.name, sync)
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
        /** What a sync sets from WarEra data, see [RoleSyncService]. */
        val WARERA_FEATURES = listOf(BitsFeature.ROLE_SYNC, BitsFeature.NICKNAMES)

        /** What a sync sets; the verification decides about the status roles. */
        val SYNCED_FEATURES = WARERA_FEATURES + BitsFeature.VERIFICATION
    }
}
