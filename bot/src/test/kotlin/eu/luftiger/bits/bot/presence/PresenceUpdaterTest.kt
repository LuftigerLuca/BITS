package eu.luftiger.bits.bot.presence

import eu.luftiger.bits.bot.backend.PresenceBackend
import eu.luftiger.bits.shared.BotActivity
import eu.luftiger.bits.shared.BotActivityType
import eu.luftiger.bits.shared.BotPresence
import eu.luftiger.bits.shared.BotPresenceChangedEvent
import eu.luftiger.bits.shared.BotStatus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceUpdaterTest {
    @Test
    fun `every activity type becomes the matching Discord activity`() {
        val expected =
            mapOf(
                BotActivityType.PLAYING to Activity.ActivityType.PLAYING,
                BotActivityType.WATCHING to Activity.ActivityType.WATCHING,
                BotActivityType.LISTENING to Activity.ActivityType.LISTENING,
                BotActivityType.COMPETING to Activity.ActivityType.COMPETING,
                BotActivityType.CUSTOM to Activity.ActivityType.CUSTOM_STATUS,
            )
        BotActivityType.entries.forEach { type ->
            val activity = PresenceUpdater.activity(BotPresence(activity = BotActivity(type, "WarEra")))!!
            assertEquals(expected.getValue(type), activity.type)
            assertEquals("WarEra", activity.name)
        }
        assertEquals(null, PresenceUpdater.activity(BotPresence()))
    }

    @Test
    fun `every status becomes the matching Discord status`() {
        assertEquals(
            listOf(OnlineStatus.ONLINE, OnlineStatus.IDLE, OnlineStatus.DO_NOT_DISTURB, OnlineStatus.INVISIBLE),
            BotStatus.entries.map(PresenceUpdater::status),
        )
    }

    @Test
    fun `the presence is loaded on start, again after a failure, and changed by events`() =
        runTest {
            val jda = mockk<JDA>(relaxed = true)
            val backend = mockk<PresenceBackend>()
            var calls = 0
            coEvery { backend.botPresence() } answers {
                if (calls++ == 0) throw IllegalStateException("connection refused")
                BotPresence(BotStatus.IDLE)
            }
            val updater = PresenceUpdater(jda, backend, backgroundScope)

            updater.loadOnStart()
            runCurrent()
            verify(exactly = 0) { jda.presence.setPresence(any<OnlineStatus>(), any<Activity>()) }
            advanceTimeBy(5_001)
            verify { jda.presence.setPresence(OnlineStatus.IDLE, null) }

            updater.handle(BotPresenceChangedEvent(BotPresence(BotStatus.DO_NOT_DISTURB), occurredAt = 0))
            verify { jda.presence.setPresence(OnlineStatus.DO_NOT_DISTURB, null) }
        }
}
