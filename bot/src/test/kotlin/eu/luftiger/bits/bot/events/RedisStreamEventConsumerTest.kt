package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.encode
import io.lettuce.core.RedisClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisStreamEventConsumerTest {
    private val handled = mutableListOf<BitsEvent>()

    private fun consumer(handler: EventHandler = EventHandler { handled += it }) = RedisStreamEventConsumer(RedisClient.create(), handler)

    @Test
    fun `handled event is acknowledged`() =
        runTest {
            val event = PingEvent(discordUserId = "42", occurredAt = 0)

            assertTrue(consumer().process(event.encode()))
            assertEquals(listOf<BitsEvent>(event), handled)
        }

    @Test
    fun `failed event stays pending`() =
        runTest {
            val consumer = consumer { error("Discord is down") }

            assertFalse(consumer.process(PingEvent(discordUserId = "42", occurredAt = 0).encode()))
        }

    @Test
    fun `unreadable entries are acknowledged without handling`() =
        runTest {
            assertTrue(consumer().process("""{"type":"unknown"}"""))
            assertTrue(consumer().process(null))
            assertEquals(emptyList(), handled)
        }
}
