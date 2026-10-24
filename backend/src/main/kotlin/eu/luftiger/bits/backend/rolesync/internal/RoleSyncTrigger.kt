package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import java.util.concurrent.Executors

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
    @TransactionalEventListener
    fun onAccountLinked(event: AccountLinked) =
        submit { syncService.syncMembers(listOfNotNull(event.discordUserId, event.previousDiscordUserId)) }

    @Scheduled(cron = "\${bits.role-sync.cron}")
    fun hourly() = submit { syncService.syncAll(refresh = true) }

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
