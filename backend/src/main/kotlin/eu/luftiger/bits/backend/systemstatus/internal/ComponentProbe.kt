package eu.luftiger.bits.backend.systemstatus.internal

import com.zaxxer.hikari.HikariDataSource
import eu.luftiger.bits.backend.systemstatus.api.EventStreamStatus
import eu.luftiger.bits.backend.systemstatus.api.dto.EventStreamState
import jakarta.annotation.PreDestroy
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Asks Postgres and Redis every [INTERVAL] whether they answer, what they hold and how far the bot is behind on events. On a thread of its own, since a
 * database that is down keeps a connection waiting for up to 30 seconds, which would hold up the scheduled jobs. The
 * page "System-Status" shows the last result, so it loads at once even then.
 */
@Component
class ComponentProbe(
    private val jdbc: JdbcTemplate,
    private val dataSource: DataSource,
    private val redis: StringRedisTemplate,
    private val flyway: ObjectProvider<Flyway>,
    private val eventStream: ObjectProvider<EventStreamStatus>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "system-status-probe").apply { isDaemon = true } }

    @Volatile
    private var postgres: Probe<PostgresInfo>? = null

    fun postgres(): Probe<PostgresInfo>? = postgres

    @Volatile
    private var redisProbe: Probe<RedisInfo>? = null

    fun redisProbe(): Probe<RedisInfo>? = redisProbe

    // Read here too, since counting the outbox needs Postgres.
    @Volatile
    private var events: EventStreamState? = null

    fun events(): EventStreamState? = events

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        executor.scheduleWithFixedDelay(::probe, 0, INTERVAL.toMillis(), TimeUnit.MILLISECONDS)
    }

    fun probe() {
        postgres = check(postgres, ::postgresInfo)
        redisProbe = check(redisProbe, ::redisInfo)
        events =
            try {
                eventStream.ifAvailable?.state()
            } catch (e: Exception) {
                log.debug("Could not read the state of the event stream", e)
                null
            }
    }

    private fun <T> check(
        previous: Probe<T>?,
        query: () -> T,
    ): Probe<T> {
        val start = System.nanoTime()
        val (info, problem) =
            try {
                query() to null
            } catch (e: Exception) {
                log.debug("System status probe failed", e)
                null to (e.message?.lineSequence()?.first() ?: e::class.simpleName.orEmpty())
            }
        val now = clock.instant()
        val up = problem == null
        return Probe(
            up = up,
            since = if (previous?.up == up) previous.since else now,
            checkedAt = now,
            duration = Duration.ofNanos(System.nanoTime() - start),
            lastFailure = if (up) previous?.lastFailure else now,
            lastProblem = problem ?: previous?.lastProblem,
            // What was read last stays, so the page still shows the sizes while the component is down.
            info = info ?: previous?.info,
        )
    }

    private fun postgresInfo(): PostgresInfo {
        jdbc.queryForObject("SELECT 1", Int::class.java)
        val size = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long::class.java)
        val pool = runCatching { dataSource.unwrap(HikariDataSource::class.java).hikariPoolMXBean }.getOrNull()
        return PostgresInfo(
            sizeBytes = size,
            schemaVersion =
                flyway.ifAvailable
                    ?.info()
                    ?.current()
                    ?.version
                    ?.version,
            poolActive = pool?.activeConnections,
            poolIdle = pool?.idleConnections,
            poolWaiting = pool?.threadsAwaitingConnection,
            poolMax = runCatching { dataSource.unwrap(HikariDataSource::class.java).maximumPoolSize }.getOrNull(),
        )
    }

    private fun redisInfo(): RedisInfo =
        redis.execute(
            RedisCallback { connection: RedisConnection ->
                connection.ping()
                val memory = connection.serverCommands().info("memory")
                RedisInfo(
                    usedMemoryBytes = memory?.getProperty("used_memory")?.toLongOrNull(),
                    maxMemoryBytes = memory?.getProperty("maxmemory")?.toLongOrNull()?.takeIf { it > 0 },
                )
            },
        ) ?: RedisInfo(null, null)

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    /** [lastProblem] belongs to [lastFailure]; [info] is from the last check that worked. */
    data class Probe<T>(
        val up: Boolean,
        val since: Instant,
        val checkedAt: Instant,
        val duration: Duration,
        val lastFailure: Instant?,
        val lastProblem: String?,
        val info: T?,
    )

    data class PostgresInfo(
        val sizeBytes: Long?,
        val schemaVersion: String?,
        val poolActive: Int?,
        val poolIdle: Int?,
        val poolWaiting: Int?,
        val poolMax: Int?,
    )

    data class RedisInfo(
        val usedMemoryBytes: Long?,
        val maxMemoryBytes: Long?,
    )

    companion object {
        val INTERVAL: Duration = Duration.ofSeconds(15)
    }
}
