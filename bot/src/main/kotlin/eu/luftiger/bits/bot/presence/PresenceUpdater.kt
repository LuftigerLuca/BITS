package eu.luftiger.bits.bot.presence

import eu.luftiger.bits.bot.backend.PresenceBackend
import eu.luftiger.bits.shared.BotActivityType
import eu.luftiger.bits.shared.BotPresence
import eu.luftiger.bits.shared.BotPresenceChangedEvent
import eu.luftiger.bits.shared.BotStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import org.slf4j.LoggerFactory

/**
 * Shows the bot in Discord with the status and activity an admin set in the dashboard: asked from the backend on start,
 * with backoff until it answers, and changed with every [BotPresenceChangedEvent].
 */
class PresenceUpdater(
    private val jda: JDA,
    private val backend: PresenceBackend,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun loadOnStart() {
        scope.launch {
            var backoff = INITIAL_BACKOFF_MILLIS
            while (true) {
                try {
                    apply(backend.botPresence())
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("Could not load the bot presence, trying again in {} ms", backoff, e)
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
                }
            }
        }
    }

    fun handle(event: BotPresenceChangedEvent) = apply(event.presence)

    private fun apply(presence: BotPresence) {
        jda.presence.setPresence(status(presence.status), activity(presence))
        log.info("Bot presence set to {} with activity {}", presence.status, presence.activity?.type)
    }

    internal companion object {
        private const val INITIAL_BACKOFF_MILLIS = 5_000L
        private const val MAX_BACKOFF_MILLIS = 300_000L

        fun status(status: BotStatus): OnlineStatus =
            when (status) {
                BotStatus.ONLINE -> OnlineStatus.ONLINE
                BotStatus.IDLE -> OnlineStatus.IDLE
                BotStatus.DO_NOT_DISTURB -> OnlineStatus.DO_NOT_DISTURB
                BotStatus.INVISIBLE -> OnlineStatus.INVISIBLE
            }

        /** `null` for no activity. */
        fun activity(presence: BotPresence): Activity? =
            presence.activity?.let {
                when (it.type) {
                    BotActivityType.PLAYING -> Activity.playing(it.text)
                    BotActivityType.WATCHING -> Activity.watching(it.text)
                    BotActivityType.LISTENING -> Activity.listening(it.text)
                    BotActivityType.COMPETING -> Activity.competing(it.text)
                    BotActivityType.CUSTOM -> Activity.customStatus(it.text)
                }
            }
    }
}
