package eu.luftiger.bits.backend.embassy.internal

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bits.embassy")
data class EmbassyProperties(
    /** When the embassy threads are synced with fresh governments, as a Spring cron expression; `-` disables it. */
    val cron: String,
)
