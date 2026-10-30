package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import eu.luftiger.bits.backend.account.api.AccountUnlinked
import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggled
import eu.luftiger.bits.backend.feature.api.FeatureToggles
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

/**
 * Starts embassy syncs in the background, one at a time on a thread of their own: right after a member links or
 * unlinks an account, after an admin changes something on the embassy page or the bot created a thread, and every
 * hour. A sync also asks the bot for missing threads, see [EmbassySyncService].
 *
 * Every sync covers all threads, since a government has a handful of offices and the [eu.luftiger.bits.warera.WareraClient]
 * keeps it for a few minutes. Requests that arrive while a sync runs are merged into one that follows it, with the
 * trigger of the first of them.
 *
 * While an admin has switched the embassies off, requests are dropped; switching them on starts a sync.
 */
@Component
class EmbassySyncTrigger(
    private val syncService: EmbassySyncService,
    private val systemLog: SystemLog,
    private val features: FeatureToggles,
    private val work: BackgroundWork,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val syncs = Executors.newSingleThreadExecutor { Thread(it, "embassy-sync") }

    private val lock = Any()
    private var pending: SyncTrigger? = null
    private val newWareraUserIds = HashSet<String>()

    /** [newWareraUserId] is a user linked just now, whose country may need an embassy thread. */
    fun request(
        trigger: SyncTrigger,
        newWareraUserId: String? = null,
    ) {
        if (!features.isEnabled(BitsFeature.EMBASSIES)) return
        synchronized(lock) {
            newWareraUserId?.let { newWareraUserIds += it }
            if (pending != null) return
            pending = trigger
        }
        syncs.execute(::run)
    }

    @ApplicationModuleListener
    fun onAccountLinked(event: AccountLinked) = request(SyncTrigger.ACCOUNT_CHANGE, event.wareraUserId)

    @ApplicationModuleListener
    fun onAccountUnlinked(event: AccountUnlinked) = request(SyncTrigger.ACCOUNT_CHANGE)

    @ApplicationModuleListener
    fun onFeatureToggled(event: FeatureToggled) {
        if (event.feature == BitsFeature.EMBASSIES && event.enabled) request(SyncTrigger.SETTING_CHANGE)
    }

    @Scheduled(cron = "\${bits.embassy.cron}")
    fun hourly() = request(SyncTrigger.HOURLY)

    /** Waits until all syncs started so far have finished, so that tests do not see syncs of the previous test. */
    fun awaitIdle(timeout: Duration = Duration.ofSeconds(10)) {
        syncs.submit {}.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun run() {
        // Taken right before the sync, so a request that comes in during it gets a sync of its own afterwards.
        val (trigger, newUsers) =
            synchronized(lock) {
                val trigger = pending ?: return
                pending = null
                (trigger to newWareraUserIds.toSet()).also { newWareraUserIds.clear() }
            }
        try {
            work.run(BackgroundJob.EMBASSY_SYNC, trigger.name) { syncService.sync(trigger, newUsers) }
        } catch (e: Exception) {
            log.error("Embassy sync failed", e)
            try {
                systemLog.recordProblem(SystemProblem.SyncAborted(trigger, e.message ?: e::class.simpleName.orEmpty()))
            } catch (logFailure: Exception) {
                // Likely the same cause, e.g. the database is gone.
                log.warn("Could not record the failed embassy sync in the system log", logFailure)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        syncs.shutdownNow()
    }
}
