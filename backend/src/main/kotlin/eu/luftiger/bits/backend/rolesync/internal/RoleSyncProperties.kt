package eu.luftiger.bits.backend.rolesync.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("bits.role-sync")
data class RoleSyncProperties(
    /** When the sync of all members with fresh WarEra data runs, as a Spring cron expression; `-` disables it. */
    val cron: String,
    /**
     * Pause between two user lookups of a sync. WarEra allows 100 requests per minute, so one per second leaves room for
     * the other calls of the backend.
     */
    val userLookupInterval: Duration = Duration.ofSeconds(1),
)
