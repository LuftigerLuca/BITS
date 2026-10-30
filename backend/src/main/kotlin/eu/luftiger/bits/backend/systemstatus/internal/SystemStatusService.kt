package eu.luftiger.bits.backend.systemstatus.internal

import eu.luftiger.bits.backend.systemstatus.api.BackgroundJob
import eu.luftiger.bits.warera.RestWareraClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.config.ScheduledTaskHolder
import org.springframework.scheduling.config.TaskExecutionOutcome
import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Puts together the page "System-Status" from what is kept in memory, so it answers at once and asks no component
 * itself: Postgres, Redis and the event stream come from [ComponentProbe], WarEra from its last calls, the bot from its
 * last heartbeat.
 */
@Service
class SystemStatusService(
    private val probe: ComponentProbe,
    private val warera: WareraStatistics,
    private val requests: RequestStatisticsFilter,
    private val heartbeats: BotHeartbeats,
    private val work: BackgroundWorkTracker,
    private val scheduledTasks: ObjectProvider<ScheduledTaskHolder>,
    private val clock: Clock,
    @param:Value("\${bits.version:dev}") private val version: String,
) {
    fun status(): SystemStatusResponse =
        SystemStatusResponse(
            generatedAt = clock.millis(),
            backend = backendProcess(),
            components = listOf(postgres(), redis(), warera(), bot()),
            postgres =
                probe.postgres()?.info?.let {
                    PostgresDetails(it.sizeBytes, it.schemaVersion, it.poolActive, it.poolIdle, it.poolWaiting, it.poolMax)
                },
            redis = probe.redisProbe()?.info?.let { RedisDetails(it.usedMemoryBytes, it.maxMemoryBytes) },
            bot = botDetails(),
            warera = wareraDetails(),
            api = apiDetails(),
            events = eventDetails(),
            jobs = jobs(),
            scheduledTasks = scheduledTasks(),
        )

    private fun backendProcess(): ProcessInfo {
        val memory = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        return ProcessInfo(
            version = version,
            startedAt = ManagementFactory.getRuntimeMXBean().startTime,
            heapUsedBytes = memory.used,
            heapMaxBytes = memory.max,
            threads = ManagementFactory.getThreadMXBean().threadCount,
        )
    }

    private fun postgres() = probe.postgres().toStatus(Component.POSTGRES)

    private fun redis() = probe.redisProbe().toStatus(Component.REDIS)

    private fun ComponentProbe.Probe<*>?.toStatus(component: Component) =
        ComponentStatus(
            component = component,
            up = this?.up,
            since = this?.since?.toEpochMilli(),
            checkedAt = this?.checkedAt?.toEpochMilli(),
            durationMillis = this?.duration?.toMillis(),
            lastFailureAt = this?.lastFailure?.toEpochMilli(),
            lastProblem = this?.lastProblem,
        )

    private fun warera(): ComponentStatus {
        val reachability = warera.reachability()
        return ComponentStatus(
            component = Component.WARERA,
            up = reachability?.reachable,
            since = reachability?.since?.toEpochMilli(),
            checkedAt = reachability?.lastContact?.toEpochMilli(),
            durationMillis = reachability?.lastDuration?.toMillis(),
            lastFailureAt = reachability?.lastFailure?.toEpochMilli(),
            lastProblem = reachability?.lastProblem,
        )
    }

    /** Offline without a heartbeat for [BotHeartbeats.OFFLINE_AFTER]; since then, as far as it can be told. */
    private fun bot(): ComponentStatus {
        val last = heartbeats.last()
        val online = heartbeats.online()
        val since =
            when {
                last == null -> null
                online -> Instant.ofEpochMilli(last.heartbeat.startedAt).let { if (it.isAfter(last.receivedAt)) last.receivedAt else it }
                else -> last.receivedAt
            }
        return ComponentStatus(
            component = Component.BOT,
            up = if (last == null) null else online,
            since = since?.toEpochMilli(),
            checkedAt = last?.receivedAt?.toEpochMilli(),
            durationMillis = last?.heartbeat?.gatewayPingMillis?.takeIf { it >= 0 },
            lastFailureAt = null,
            lastProblem =
                if (last != null &&
                    !online
                ) {
                    "Kein Heartbeat seit ${Duration.between(last.receivedAt, clock.instant()).toSeconds()} s"
                } else {
                    null
                },
        )
    }

    private fun botDetails(): BotDetails? {
        val (heartbeat) = heartbeats.last() ?: return null
        return BotDetails(
            process =
                ProcessInfo(
                    heartbeat.version,
                    heartbeat.startedAt,
                    heartbeat.heapUsedBytes,
                    heartbeat.heapMaxBytes,
                    heartbeat.threads,
                ),
            gatewayStatus = heartbeat.gatewayStatus,
            gatewayPingMillis = heartbeat.gatewayPingMillis,
            guildMembers = heartbeat.guildMembers,
            backendCalls =
                heartbeat.backendCalls
                    .map { BotCallStats(it.endpoint, it.succeeded, it.failed, it.timedOut, it.unreachable) }
                    .sortedByDescending { it.succeeded + it.failed + it.timedOut + it.unreachable },
            discord =
                heartbeat.discordRequests.let { DiscordStats(it.total, it.rateLimited, it.refused, it.serverErrors) },
            failureHistory = heartbeats.backendCallFailures.history(),
            discordHistory = heartbeats.discordRequests.history(),
        )
    }

    private fun wareraDetails(): WareraDetails {
        fun UsageCounters.totals() = snapshot().associate { it.key to it.total }
        val failures = warera.failures.totals()
        val rateLimited = warera.rateLimited.totals()
        val retries = warera.retries.totals()
        val throttled = warera.throttled.totals()
        val hits = warera.cacheHits.totals()
        val misses = warera.cacheMisses.totals()
        val requests = warera.requests.snapshot().associateBy { it.key }
        // Procedures that only the cache answered or that were throttled show up as well.
        val procedures = requests.keys + throttled.keys + hits.keys + misses.keys
        return WareraDetails(
            limitPerMinute = RestWareraClient.DEFAULT_CALLS_PER_MINUTE,
            lastMinute = warera.requests.recent(1),
            peakPerMinute = warera.requests.peakPerMinute(),
            lastHour = warera.requests.recent(),
            history = warera.requests.history(),
            procedures =
                procedures
                    .map { procedure ->
                        ProcedureStats(
                            procedure = procedure,
                            total = requests[procedure]?.total ?: 0,
                            lastHour = requests[procedure]?.lastHour ?: 0,
                            failures = failures[procedure] ?: 0,
                            rateLimited = rateLimited[procedure] ?: 0,
                            retries = retries[procedure] ?: 0,
                            throttled = throttled[procedure] ?: 0,
                            cacheHits = hits[procedure] ?: 0,
                            cacheMisses = misses[procedure] ?: 0,
                            latency = warera.latency(procedure)?.toStats(),
                        )
                    }.sortedByDescending { it.total },
            callers =
                warera.callers
                    .snapshot()
                    .map { CallerStats(WareraCaller.valueOf(it.key), it.total, it.lastHour) }
                    .sortedByDescending { it.total },
            permitWait = warera.permitWait.summary()?.toStats(),
        )
    }

    private fun apiDetails(): ApiDetails {
        val clientErrors = requests.clientErrors.snapshot().associate { it.key to it.total }
        val serverErrors = requests.serverErrors.snapshot().associate { it.key to it.total }
        return ApiDetails(
            history = requests.requests.history(),
            endpoints =
                requests.requests
                    .snapshot()
                    .map {
                        EndpointStats(
                            endpoint = it.key,
                            total = it.total,
                            lastHour = it.lastHour,
                            clientErrors = clientErrors[it.key] ?: 0,
                            serverErrors = serverErrors[it.key] ?: 0,
                            latency = requests.latency(it.key)?.toStats(),
                        )
                    }.sortedByDescending { it.total },
        )
    }

    private fun eventDetails(): EventDetails {
        val state = probe.events()
        return EventDetails(
            history = work.events.history(),
            published =
                work.events
                    .snapshot()
                    .map { EventTypeStats(it.key, it.total, it.lastHour) }
                    .sortedByDescending { it.total },
            outboxPending = state?.outboxPending,
            outboxOldestAt = state?.outboxOldest?.toEpochMilli(),
            streamLength = state?.streamLength,
            deadLetters = state?.deadLetters,
            groups = state?.groups?.map { ConsumerGroupStats(it.name, it.consumers, it.pending, it.lag) },
        )
    }

    private fun jobs(): List<JobStatus> {
        val states = work.jobs()
        return BackgroundJob.entries.map { job ->
            val state = states[job]
            val last = state?.lastRun
            JobStatus(
                job = job,
                runningSince = state?.runningSince?.toEpochMilli(),
                runningTrigger = state?.runningTrigger,
                lastStartedAt = last?.startedAt?.toEpochMilli(),
                lastDurationMillis = last?.duration?.toMillis(),
                lastTrigger = last?.trigger,
                lastFailure = last?.failure,
                runs = state?.runs ?: 0,
                failures = state?.failures ?: 0,
            )
        }
    }

    private fun scheduledTasks(): List<ScheduledTaskStatus> =
        scheduledTasks
            .flatMap { it.scheduledTasks }
            .map { scheduled ->
                val runnable = scheduled.task.runnable
                val outcome = scheduled.task.lastExecutionOutcome
                ScheduledTaskStatus(
                    // Spring wraps the method, but names it as class and method.
                    name =
                        runnable
                            .toString()
                            .split('.')
                            .takeLast(2)
                            .joinToString("."),
                    nextAt = scheduled.nextExecution()?.toEpochMilli(),
                    lastAt = outcome.executionTime()?.toEpochMilli(),
                    lastOutcome = outcome.status().takeIf { it != TaskExecutionOutcome.Status.NONE }?.name,
                )
            }.sortedBy { it.name }

    private fun LatencyHistogram.Latency.toStats() = LatencyStats(p50Millis, p95Millis, maxMillis)
}
