package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Starts syncs in the background, one after the other: right away for a member who links an account, for everybody
 * after a rule change, and every hour with fresh WarEra data.
 */
@Component
class RoleSyncTrigger(
    private val syncService: RoleSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "role-sync") }

    fun syncAllWithCachedData() = submit { syncService.syncAll(refresh = false) }

    /** The previous Discord account of a relinked WarEra account loses its roles. */
    @ApplicationModuleListener
    fun onAccountLinked(event: AccountLinked) =
        submit { syncService.syncMembers(listOfNotNull(event.discordUserId, event.previousDiscordUserId)) }

    @Scheduled(cron = "\${bits.role-sync.cron}")
    fun hourly() = submit { syncService.syncAll(refresh = true) }

    /** Waits until all syncs started so far have finished, so that tests do not see syncs of the previous test. */
    fun awaitIdle(timeout: Duration = Duration.ofSeconds(10)) {
        executor.submit {}.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun submit(sync: () -> Unit) {
        executor.execute {
            try {
                sync()
            } catch (e: Exception) {
                log.error("Role sync failed", e)
            }
        }
    }

    @PreDestroy
    fun shutdown() = executor.shutdownNow()
}
