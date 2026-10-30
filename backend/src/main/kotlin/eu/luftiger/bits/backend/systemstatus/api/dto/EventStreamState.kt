package eu.luftiger.bits.backend.systemstatus.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

/** Each part is `null` if it could not be read, e.g. since Redis or Postgres is down. */
@NamedInterface("api")
data class EventStreamState(
    /** Events stored with their transaction that are not in Redis yet. */
    val outboxPending: Long?,
    val outboxOldest: Instant?,
    val streamLength: Long?,
    /** Events the bot gave up on. */
    val deadLetters: Long?,
    /** Consumer groups of the bot, `null` if Redis could not be asked. */
    val groups: List<ConsumerGroupState>?,
)

@NamedInterface("api")
data class ConsumerGroupState(
    val name: String,
    val consumers: Long,
    /** Delivered to the bot but not acknowledged yet, i.e. being handled or retried. */
    val pending: Long,
    /** Not delivered to the bot yet; `null` if Redis cannot tell. */
    val lag: Long?,
)
