package eu.luftiger.bits.backend.systemstatus.internal

import eu.luftiger.bits.backend.systemstatus.api.BackgroundJob
import kotlinx.serialization.Serializable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The page "System-Status" in the dashboard, read-only. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/system-status")
class SystemStatusController(
    private val service: SystemStatusService,
) {
    @GetMapping
    fun status(): SystemStatusResponse = service.status()
}

/**
 * Times are epoch milliseconds. Counts are since the backend started ([ProcessInfo.startedAt] of [backend]), the
 * histories in buckets of 15 minutes over the last 24 hours, oldest first.
 */
@Serializable
data class SystemStatusResponse(
    val generatedAt: Long,
    val backend: ProcessInfo,
    val components: List<ComponentStatus>,
    val postgres: PostgresDetails?,
    val redis: RedisDetails?,
    val bot: BotDetails?,
    val warera: WareraDetails,
    val api: ApiDetails,
    val events: EventDetails,
    val jobs: List<JobStatus>,
    val scheduledTasks: List<ScheduledTaskStatus>,
)

@Serializable
data class ProcessInfo(
    /** The commit it was built from, `dev` outside of a deployment. */
    val version: String,
    val startedAt: Long,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val threads: Int,
)

@Serializable
enum class Component { POSTGRES, REDIS, WARERA, BOT }

/**
 * [up] is `null` while nothing is known yet, e.g. before the first WarEra call. [checkedAt] is the last check, the
 * last WarEra call or the last heartbeat of the bot; [since] when [up] last changed.
 */
@Serializable
data class ComponentStatus(
    val component: Component,
    val up: Boolean?,
    val since: Long?,
    val checkedAt: Long?,
    val durationMillis: Long?,
    val lastFailureAt: Long?,
    val lastProblem: String?,
)

@Serializable
data class PostgresDetails(
    val sizeBytes: Long?,
    /** The version of the last Flyway migration. */
    val schemaVersion: String?,
    val poolActive: Int?,
    val poolIdle: Int?,
    val poolWaiting: Int?,
    val poolMax: Int?,
)

@Serializable
data class RedisDetails(
    val usedMemoryBytes: Long?,
    /** `null` without a limit. */
    val maxMemoryBytes: Long?,
)

/** As of the last heartbeat; the counts in it are since the bot started. */
@Serializable
data class BotDetails(
    val process: ProcessInfo,
    val gatewayStatus: String,
    val gatewayPingMillis: Long,
    val guildMembers: Int?,
    val backendCalls: List<BotCallStats>,
    val discord: DiscordStats,
    /** Requests to the backend that failed, from the bot's view. */
    val failureHistory: List<Long>,
    val discordHistory: List<Long>,
)

@Serializable
data class BotCallStats(
    val endpoint: String,
    val succeeded: Long,
    val failed: Long,
    val timedOut: Long,
    val unreachable: Long,
)

@Serializable
data class DiscordStats(
    val total: Long,
    val rateLimited: Long,
    val refused: Long,
    val serverErrors: Long,
)

@Serializable
data class WareraDetails(
    /** What BITS allows itself per minute, below WarEra's own limit. */
    val limitPerMinute: Int,
    val lastMinute: Long,
    val peakPerMinute: Long,
    val lastHour: Long,
    val history: List<Long>,
    val procedures: List<ProcedureStats>,
    val callers: List<CallerStats>,
    /** How long requests waited for the rate limiter. */
    val permitWait: LatencyStats?,
)

/** [total] counts requests, retries included; the cache counts are lookups that it answered or passed on. */
@Serializable
data class ProcedureStats(
    val procedure: String,
    val total: Long,
    val lastHour: Long,
    val failures: Long,
    val rateLimited: Long,
    val retries: Long,
    val throttled: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val latency: LatencyStats?,
)

@Serializable
data class CallerStats(
    val caller: WareraCaller,
    val total: Long,
    val lastHour: Long,
)

@Serializable
data class LatencyStats(
    val p50Millis: Long,
    val p95Millis: Long,
    val maxMillis: Long,
)

@Serializable
data class ApiDetails(
    val history: List<Long>,
    val endpoints: List<EndpointStats>,
)

/** [endpoint] is method and path pattern, `*` for requests that reached no endpoint, e.g. without the token. */
@Serializable
data class EndpointStats(
    val endpoint: String,
    val total: Long,
    val lastHour: Long,
    val clientErrors: Long,
    val serverErrors: Long,
    val latency: LatencyStats?,
)

/** The parts that could not be read, e.g. while Redis is down, are `null`. */
@Serializable
data class EventDetails(
    val history: List<Long>,
    val published: List<EventTypeStats>,
    val outboxPending: Long?,
    val outboxOldestAt: Long?,
    val streamLength: Long?,
    val deadLetters: Long?,
    val groups: List<ConsumerGroupStats>?,
)

@Serializable
data class EventTypeStats(
    val type: String,
    val total: Long,
    val lastHour: Long,
)

@Serializable
data class ConsumerGroupStats(
    val name: String,
    val consumers: Long,
    val pending: Long,
    val lag: Long?,
)

/** [trigger] is the name of a sync trigger, as in the system log, if the job has one. */
@Serializable
data class JobStatus(
    val job: BackgroundJob,
    val runningSince: Long?,
    val runningTrigger: String?,
    val lastStartedAt: Long?,
    val lastDurationMillis: Long?,
    val lastTrigger: String?,
    /** `null` if the last run went through. */
    val lastFailure: String?,
    val runs: Long,
    val failures: Long,
)

/** A job on a schedule; [name] is class and method, e.g. `RoleSyncTrigger.hourly`. */
@Serializable
data class ScheduledTaskStatus(
    val name: String,
    val nextAt: Long?,
    val lastAt: Long?,
    /** `STARTED`, `SUCCESS` or `ERROR`, `null` before the first run. */
    val lastOutcome: String?,
)
