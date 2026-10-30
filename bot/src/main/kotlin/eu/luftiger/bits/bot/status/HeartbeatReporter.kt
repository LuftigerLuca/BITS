package eu.luftiger.bits.bot.status

import eu.luftiger.bits.bot.backend.HeartbeatBackend
import eu.luftiger.bits.shared.BotHeartbeat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.time.Clock

/**
 * Tells the backend every [PERIOD_MILLIS] that the bot runs, for the page "System-Status". A heartbeat the backend
 * does not take is dropped; the next one carries the counts since the start anyway.
 */
class HeartbeatReporter(
    private val jda: JDA,
    private val guildId: String,
    private val backend: HeartbeatBackend,
    private val backendCalls: BackendCallCounter,
    private val discordRequests: DiscordRequestCounter,
    private val version: String,
    private val scope: CoroutineScope,
    clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val startedAt = clock.millis()

    @Volatile
    private var failing = false

    fun reportPeriodically() {
        scope.launch {
            while (true) {
                report()
                delay(PERIOD_MILLIS)
            }
        }
    }

    suspend fun report() {
        try {
            backend.heartbeat(heartbeat())
            if (failing) log.info("Heartbeats reach the backend again")
            failing = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Once per outage, not every heartbeat.
            if (!failing) log.warn("Could not send the heartbeat to the backend", e)
            failing = true
        }
    }

    fun heartbeat(): BotHeartbeat {
        val memory = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        return BotHeartbeat(
            version = version,
            startedAt = startedAt,
            gatewayStatus = jda.status.name,
            gatewayPingMillis = jda.gatewayPing,
            guildMembers = jda.getGuildById(guildId)?.memberCount,
            heapUsedBytes = memory.used,
            heapMaxBytes = memory.max,
            threads = ManagementFactory.getThreadMXBean().threadCount,
            backendCalls = backendCalls.snapshot(),
            discordRequests = discordRequests.snapshot(),
        )
    }

    private companion object {
        const val PERIOD_MILLIS = 30_000L
    }
}
