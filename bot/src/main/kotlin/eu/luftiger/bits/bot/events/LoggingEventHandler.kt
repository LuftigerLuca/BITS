package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.PingEvent
import org.slf4j.LoggerFactory

/** Example handler: only logs the events it receives. */
class LoggingEventHandler : EventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun handle(event: BitsEvent) {
        when (event) {
            is PingEvent -> log.info("Backend handled a ping of user {}", event.discordUserId)
        }
    }
}
