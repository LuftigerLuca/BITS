package eu.luftiger.bits.bot.events

import com.github.dockerjava.api.model.PortBinding
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.EventStream
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.encode
import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.net.ServerSocket
import java.time.Duration
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs against a real Redis, since the consumer is mostly about how Redis streams behave. */
class RedisStreamEventConsumerTest {
    private val handled: MutableList<BitsEvent> = Collections.synchronizedList(mutableListOf())
    private val clients = mutableListOf<RedisClient>()

    /** Consumers run until the test ends, outside of the test's own coroutine, which would wait for them. */
    private val consumers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var redis: RedisCommands<String, String>

    private val settings =
        RedisStreamEventConsumer.Settings(
            blockTimeout = Duration.ofMillis(100),
            retryAfter = Duration.ofMillis(200),
            maxDeliveries = 3,
            minReconnectDelay = Duration.ofMillis(100),
            maxReconnectDelay = Duration.ofMillis(200),
        )

    @BeforeTest
    fun connect() {
        redis = client(REDIS_URL).connect().sync()
        redis.flushall()
    }

    @AfterTest
    fun close() =
        runBlocking {
            // Shutting the clients down first ends blocking reads right away.
            clients.forEach { it.shutdown() }
            consumers.coroutineContext[Job]!!.cancelAndJoin()
        }

    @Test
    fun `handled event is acknowledged`() =
        runBlocking {
            start()
            val event = ping("1")

            publish(event)

            awaitHandled(event)
            awaitNoPending()
        }

    @Test
    fun `failing event is retried, then moved to the dead letter stream, and does not block newer ones`() =
        runBlocking {
            var attempts = 0
            start(
                consumer {
                    if ((it as PingEvent).discordUserId == "broken") {
                        attempts++
                        error("Discord is down")
                    }
                    handled += it
                },
            )

            publish(ping("broken"))
            publish(ping("2"))

            awaitHandled(ping("2"))
            eventually { assertEquals(1, redis.xlen(EventStream.DEAD_LETTER_KEY)) }
            assertEquals(3, attempts)
            awaitNoPending()
            val dead = redis.xrange(EventStream.DEAD_LETTER_KEY, Range.unbounded()).single()
            assertEquals(ping("broken").encode(), dead.body[EventStream.PAYLOAD_FIELD])
        }

    @Test
    fun `event left pending by a crash is handled after the restart, next to new ones`() =
        runBlocking {
            val first = start(consumer { error("crash") })
            publish(ping("1"))
            eventually { assertEquals(1, redis.xpending(EventStream.KEY, EventStream.BOT_GROUP).count) }
            first.cancelAndJoin()

            start()
            publish(ping("2"))

            awaitHandled(ping("2"), ping("1"))
            awaitNoPending()
        }

    @Test
    fun `unknown event types stay pending until they run out of deliveries`() =
        runBlocking {
            start()

            redis.xadd(EventStream.KEY, mapOf(EventStream.PAYLOAD_FIELD to """{"type":"from_the_future","occurredAt":0}"""))

            eventually { assertEquals(1, redis.xlen(EventStream.DEAD_LETTER_KEY)) }
            assertEquals(emptyList(), handled)
        }

    @Test
    fun `consumer started before Redis handles events once Redis is up`() =
        runBlocking {
            val port = ServerSocket(0).use { it.localPort }
            consumers.launch { consumer(client = client("redis://localhost:$port")).run() }
            delay(300)

            GenericContainer(IMAGE)
                // Without an exposed port Testcontainers does not wait for Redis by itself.
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
                .withCreateContainerCmdModifier { it.hostConfig!!.withPortBindings(PortBinding.parse("$port:6379")) }
                .use { late ->
                    late.start()
                    val lateRedis = client("redis://localhost:$port").connect().sync()
                    eventually { assertTrue(lateRedis.exists(EventStream.KEY) == 1L) }

                    lateRedis.xadd(EventStream.KEY, mapOf(EventStream.PAYLOAD_FIELD to ping("1").encode()))

                    awaitHandled(ping("1"))
                }
        }

    @Test
    fun `consumer creates the group again when the stream is deleted`() =
        runBlocking {
            start()
            publish(ping("1"))
            awaitHandled(ping("1"))

            redis.del(EventStream.KEY)
            delay(300)
            publish(ping("2"))

            awaitHandled(ping("1"), ping("2"))
        }

    @Test
    fun `stopping lets the event at hand finish and acknowledges it`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val consumer =
                consumer {
                    started.complete(Unit)
                    release.await()
                    handled += it
                }
            val job = start(consumer)
            publish(ping("1"))
            started.await()

            consumer.stop()
            release.complete(Unit)

            withTimeout(2_000) { job.join() }
            assertEquals(listOf<BitsEvent>(ping("1")), handled)
            awaitNoPending()
        }

    @Test
    fun `unreadable entries are not handled`() =
        runTest {
            assertFalse(consumer().process("1-0", """{"type":"unknown"}"""))
            assertFalse(consumer().process("1-0", null))
            assertTrue(consumer().process("1-0", ping("1").encode()))
            assertEquals(listOf<BitsEvent>(ping("1")), handled)
        }

    private fun consumer(
        client: RedisClient = client(REDIS_URL),
        handler: EventHandler = EventHandler { handled += it },
    ) = RedisStreamEventConsumer(client, handler, settings)

    private fun consumer(handler: EventHandler) = consumer(client(REDIS_URL), handler)

    private fun client(url: String) = RedisClient.create(url).also { clients += it }

    /** Starts [consumer] and waits until its group exists, so that nothing published afterwards is missed. */
    private suspend fun start(consumer: RedisStreamEventConsumer = consumer()): Job {
        val job = consumers.launch { consumer.run() }
        eventually { assertTrue(redis.exists(EventStream.KEY) == 1L && redis.xinfoGroups(EventStream.KEY).isNotEmpty()) }
        return job
    }

    private fun publish(event: BitsEvent) {
        redis.xadd(EventStream.KEY, mapOf(EventStream.PAYLOAD_FIELD to event.encode()))
    }

    private fun ping(discordUserId: String) = PingEvent(discordUserId, occurredAt = 0)

    private suspend fun awaitHandled(vararg events: BitsEvent) = eventually { assertEquals(events.toSet(), handled.toSet()) }

    private suspend fun awaitNoPending() = eventually { assertEquals(0, redis.xpending(EventStream.KEY, EventStream.BOT_GROUP).count) }

    /** Retries [assertion] for up to five seconds and fails with its last error. */
    private suspend fun eventually(assertion: () -> Unit) {
        var lastError: AssertionError? = null
        withTimeoutOrNull(5_000) {
            while (true) {
                try {
                    return@withTimeoutOrNull assertion()
                } catch (e: AssertionError) {
                    lastError = e
                    delay(50)
                }
            }
        } ?: throw lastError ?: AssertionError("timed out")
    }

    companion object {
        private const val IMAGE = "redis:8-alpine"
        private val REDIS = GenericContainer(IMAGE).withExposedPorts(6379).apply { start() }
        private val REDIS_URL = "redis://${REDIS.host}:${REDIS.getMappedPort(6379)}"
    }
}
