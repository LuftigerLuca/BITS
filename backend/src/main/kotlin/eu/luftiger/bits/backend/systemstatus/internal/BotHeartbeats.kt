package eu.luftiger.bits.backend.systemstatus.internal

import eu.luftiger.bits.shared.BotHeartbeat
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The bot's last [BotHeartbeat]. Its counts run since the bot started; the difference to the previous heartbeat of the
 * same start goes into the history. The first heartbeat after a start of the backend or the bot only sets the base.
 */
@Component
class BotHeartbeats(
    private val clock: Clock,
) {
    val backendCalls = UsageCounters(clock)

    /** By kind: `failed`, `timedOut` and `unreachable`. */
    val backendCallFailures = UsageCounters(clock)

    /** By kind: `total`, `rateLimited`, `refused` and `serverErrors`. */
    val discordRequests = UsageCounters(clock)

    @Volatile
    private var last: Received? = null

    fun last(): Received? = last

    @Synchronized
    fun receive(heartbeat: BotHeartbeat) {
        val previous = last?.heartbeat?.takeIf { it.startedAt == heartbeat.startedAt }
        if (previous != null) addDifference(previous, heartbeat)
        last = Received(heartbeat, clock.instant())
    }

    /** Whether the last heartbeat is younger than [OFFLINE_AFTER]. */
    fun online(): Boolean = last?.let { Duration.between(it.receivedAt, clock.instant()) < OFFLINE_AFTER } ?: false

    private fun addDifference(
        previous: BotHeartbeat,
        current: BotHeartbeat,
    ) {
        val before = previous.backendCalls.associateBy { it.endpoint }
        current.backendCalls.forEach { calls ->
            val old = before[calls.endpoint]
            backendCalls.add(
                calls.endpoint,
                calls.succeeded + calls.failed + calls.timedOut + calls.unreachable -
                    (old?.let { it.succeeded + it.failed + it.timedOut + it.unreachable } ?: 0),
            )
            backendCallFailures.add("failed", calls.failed - (old?.failed ?: 0))
            backendCallFailures.add("timedOut", calls.timedOut - (old?.timedOut ?: 0))
            backendCallFailures.add("unreachable", calls.unreachable - (old?.unreachable ?: 0))
        }
        val discord = current.discordRequests
        val old = previous.discordRequests
        discordRequests.add("total", discord.total - old.total)
        discordRequests.add("rateLimited", discord.rateLimited - old.rateLimited)
        discordRequests.add("refused", discord.refused - old.refused)
        discordRequests.add("serverErrors", discord.serverErrors - old.serverErrors)
    }

    data class Received(
        val heartbeat: BotHeartbeat,
        val receivedAt: Instant,
    )

    companion object {
        /** Three heartbeats missed in a row. */
        val OFFLINE_AFTER: Duration = Duration.ofSeconds(90)
    }
}

/** The bot reports itself every 30 seconds. */
@RestController
class BotHeartbeatController(
    private val heartbeats: BotHeartbeats,
) {
    @PostMapping("/api/bot-heartbeat")
    fun heartbeat(
        @RequestBody heartbeat: BotHeartbeat,
    ) = heartbeats.receive(heartbeat)
}
