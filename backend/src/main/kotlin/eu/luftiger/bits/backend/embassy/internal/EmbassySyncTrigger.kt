package eu.luftiger.bits.backend.embassy.internal

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

/**
 * Starts embassy syncs in the background, one at a time on a thread of their own: right after a member links or
 * unlinks an account, after an admin changes something on the embassy page, and every hour.
 *
 * Every sync covers all threads, since a government has a handful of offices and the [eu.luftiger.bits.warera.WareraClient]
 * keeps it for a few minutes. Requests that arrive while a sync runs are merged into one that follows it, with the
 * trigger of the first of them.
 */
@Component
class EmbassySyncTrigger(
    private val syncService: EmbassySyncService,
    private val systemLog: SystemLog,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val syncs = Executors.newSingleThreadExecutor { Thread(it, "embassy-sync") }

    private val lock = Any()
    private var pending: SyncTrigger? = null

    fun request(trigger: SyncTrigger) {
        synchronized(lock) {
            if (pending != null) return
            pending = trigger
        }
        syncs.execute(::run)
    }

    @ApplicationModuleListener
    fun onAccountLinked(event: AccountLinked) = request(SyncTrigger.ACCOUNT_CHANGE)

    @ApplicationModuleListener
    fun onAccountUnlinked(event: AccountUnlinked) = request(SyncTrigger.ACCOUNT_CHANGE)

    @Scheduled(cron = "\${bits.embassy.cron}")
    fun hourly() = request(SyncTrigger.HOURLY)

    /** Waits until all syncs started so far have finished, so that tests do not see syncs of the previous test. */
    fun awaitIdle(timeout: Duration = Duration.ofSeconds(10)) {
        syncs.submit {}.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun run() {
        // Taken right before the sync, so a request that comes in during it gets a sync of its own afterwards.
        val trigger = synchronized(lock) { pending.also { pending = null } } ?: return
        try {
            syncService.sync(trigger)
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
