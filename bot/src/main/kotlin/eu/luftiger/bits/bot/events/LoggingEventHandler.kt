package eu.luftiger.bits.bot.events

import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.EmbassySyncEvent
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.VerificationCheckedEvent
import eu.luftiger.bits.shared.VoteClosedEvent
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import eu.luftiger.bits.shared.VoteStartedEvent
import org.slf4j.LoggerFactory

/** Example handler: only logs the events it receives. */
class LoggingEventHandler : EventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun handle(event: BitsEvent) {
        when (event) {
            is PingEvent -> log.info("Backend handled a ping of user {}", event.discordUserId)
            is AccountLinkedEvent -> log.info("User {} linked a WarEra account", event.discordUserId)
            is VerificationCheckedEvent ->
                log.info("Verification check of user {}: {}", event.discordUserId, event.result::class.simpleName)
            is RoleSyncEvent ->
                log.info("Role sync for {} members, {} with target roles", event.discordUserIds?.size ?: "all", event.members.size)
            is VoteStartedEvent -> log.info("Vote {} started", event.voteId)
            is VoteClosedEvent -> log.info("Vote {} closed", event.voteId)
            is VoteEvaluatedEvent -> log.info("Vote {} evaluated", event.voteId)
            is EmbassySyncEvent -> log.info("Embassy sync {} for {} threads", event.syncId, event.threads.size)
        }
    }
}
