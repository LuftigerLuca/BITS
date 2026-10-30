package eu.luftiger.bits.backend.presence.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.presence.domain.ActivityType
import eu.luftiger.bits.backend.presence.domain.BotPresenceSetting
import eu.luftiger.bits.backend.presence.domain.PresenceStatus
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.shared.BotActivity
import eu.luftiger.bits.shared.BotActivityType
import eu.luftiger.bits.shared.BotPresence
import eu.luftiger.bits.shared.BotPresenceChangedEvent
import eu.luftiger.bits.shared.BotStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Status and activity of the bot. A change reaches the bot as [BotPresenceChangedEvent]; on start, the bot asks for the
 * presence itself, so one it missed meanwhile still applies.
 */
@Service
class BotPresenceService(
    private val repository: BotPresenceRepository,
    private val eventPublisher: EventPublisher,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /** Online without activity while no admin has set anything, as the bot was before. */
    fun presence(): BotPresence = repository.findByIdOrNull(BotPresenceSetting.SINGLE_ROW_ID)?.toDto() ?: BotPresence()

    /** @throws InvalidInputException if the text of the activity is blank or longer than Discord allows */
    fun set(
        presence: BotPresence,
        actor: AuditActor,
    ): BotPresence {
        val activity = presence.activity?.let { it.copy(text = it.text.trim()) }
        if (activity != null && (activity.text.isEmpty() || activity.text.length > BotActivity.MAX_TEXT_LENGTH)) {
            throw InvalidInputException("Activity text must have 1 to ${BotActivity.MAX_TEXT_LENGTH} characters")
        }
        val saved = BotPresence(presence.status, activity)
        transactionTemplate.executeWithoutResult {
            val setting = repository.findByIdOrNull(BotPresenceSetting.SINGLE_ROW_ID)
            if (setting == null) {
                repository.save(BotPresenceSetting(saved.status.toDomain(), activity?.type?.toDomain(), activity?.text, clock.instant()))
            } else {
                setting.status = saved.status.toDomain()
                setting.activityType = activity?.type?.toDomain()
                setting.activityText = activity?.text
                setting.updatedAt = clock.instant()
            }
            auditLog.record(actor, AuditAction.BOT_PRESENCE_CHANGED, describe(saved))
            eventPublisher.publish(BotPresenceChangedEvent(saved, clock.millis()))
        }
        return saved
    }

    private companion object {
        /** As the dashboard shows it, e.g. "Abwesend, Schaut WarEra". */
        fun describe(presence: BotPresence): String {
            val status =
                when (presence.status) {
                    BotStatus.ONLINE -> "Online"
                    BotStatus.IDLE -> "Abwesend"
                    BotStatus.DO_NOT_DISTURB -> "Bitte nicht stören"
                    BotStatus.INVISIBLE -> "Unsichtbar"
                }
            val activity =
                presence.activity?.let {
                    when (it.type) {
                        BotActivityType.PLAYING -> "Spielt ${it.text}"
                        BotActivityType.WATCHING -> "Schaut ${it.text}"
                        BotActivityType.LISTENING -> "Hört ${it.text}"
                        BotActivityType.COMPETING -> "Tritt an in ${it.text}"
                        BotActivityType.CUSTOM -> "„${it.text}“"
                    }
                } ?: "keine Aktivität"
            return "Bot-Status: $status, $activity"
        }

        fun BotPresenceSetting.toDto(): BotPresence {
            val type = activityType
            val text = activityText
            return BotPresence(status.toDto(), if (type != null && text != null) BotActivity(type.toDto(), text) else null)
        }

        fun BotStatus.toDomain() =
            when (this) {
                BotStatus.ONLINE -> PresenceStatus.ONLINE
                BotStatus.IDLE -> PresenceStatus.IDLE
                BotStatus.DO_NOT_DISTURB -> PresenceStatus.DO_NOT_DISTURB
                BotStatus.INVISIBLE -> PresenceStatus.INVISIBLE
            }

        fun PresenceStatus.toDto() =
            when (this) {
                PresenceStatus.ONLINE -> BotStatus.ONLINE
                PresenceStatus.IDLE -> BotStatus.IDLE
                PresenceStatus.DO_NOT_DISTURB -> BotStatus.DO_NOT_DISTURB
                PresenceStatus.INVISIBLE -> BotStatus.INVISIBLE
            }

        fun BotActivityType.toDomain() =
            when (this) {
                BotActivityType.PLAYING -> ActivityType.PLAYING
                BotActivityType.WATCHING -> ActivityType.WATCHING
                BotActivityType.LISTENING -> ActivityType.LISTENING
                BotActivityType.COMPETING -> ActivityType.COMPETING
                BotActivityType.CUSTOM -> ActivityType.CUSTOM
            }

        fun ActivityType.toDto() =
            when (this) {
                ActivityType.PLAYING -> BotActivityType.PLAYING
                ActivityType.WATCHING -> BotActivityType.WATCHING
                ActivityType.LISTENING -> BotActivityType.LISTENING
                ActivityType.COMPETING -> BotActivityType.COMPETING
                ActivityType.CUSTOM -> BotActivityType.CUSTOM
            }
    }
}
