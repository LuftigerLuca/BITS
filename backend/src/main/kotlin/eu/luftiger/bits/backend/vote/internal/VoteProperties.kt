package eu.luftiger.bits.backend.vote.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("bits.vote")
data class VoteProperties(
    /** When votes that are due are ended and stuck results tried again, as a Spring cron expression; `-` disables it. */
    val cron: String,
    /** How long after a failed evaluation it is tried again. */
    val retryInterval: Duration = Duration.ofMinutes(5),
    /** How long the backend waits for the bot to report the role members or post the result before asking again. */
    val botTimeout: Duration = Duration.ofMinutes(30),
)
