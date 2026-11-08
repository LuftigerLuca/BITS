package eu.luftiger.bits.backend.reprimand.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggles
import eu.luftiger.bits.backend.reprimand.domain.Reprimand
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.shared.NotFoundException
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.shared.ReprimandTimeoutEvent
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reprimands given with `/reprimand`. The bot asks here whether the member may, the reprimand is stored and counted, and
 * the bot then sets the timeout and sends the messages itself. Withdrawing one in the dashboard sends the timeout the
 * member should have left to the bot as [ReprimandTimeoutEvent].
 */
@Service
class ReprimandService(
    private val repository: ReprimandRepository,
    private val settings: ReprimandSettings,
    private val features: FeatureToggles,
    private val auditLog: AuditLog,
    private val systemLog: SystemLog,
    private val eventPublisher: EventPublisher,
    private val clock: Clock,
) {
    /** @throws InvalidInputException if the reason is blank or too long */
    @Transactional
    fun reprimand(
        actor: ReprimandParty,
        target: ReprimandParty,
        reason: String,
    ): ReprimandOutcome {
        if (!features.isEnabled(BitsFeature.REPRIMANDS)) return ReprimandOutcome.Disabled
        if (!settings.mayReprimand(actor.administrator, actor.roleIds)) return ReprimandOutcome.Forbidden
        when {
            target.discordUserId == actor.discordUserId -> return ReprimandOutcome.Protected(ProtectedReason.SELF)
            target.bot -> return ReprimandOutcome.Protected(ProtectedReason.BOT)
            target.administrator -> return ReprimandOutcome.Protected(ProtectedReason.ADMIN)
            settings.mayReprimand(false, target.roleIds) -> return ReprimandOutcome.Protected(ProtectedReason.REPRIMANDER)
        }
        val text = reason.trim()
        if (text.isEmpty() || text.length > MAX_REASON_LENGTH) throw InvalidInputException("Invalid reason")
        val timeoutMinutes = settings.timeoutMinutes()
        val now = clock.instant()
        val timeoutUntil = now + Duration.ofMinutes(timeoutMinutes.toLong())
        val saved =
            repository.save(
                Reprimand(
                    target.discordUserId,
                    target.name,
                    text,
                    actor.discordUserId,
                    actor.name,
                    now,
                    timeoutUntil,
                ),
            )
        val count = repository.countByDiscordUserIdAndWithdrawnAtIsNull(target.discordUserId)
        auditLog.record(
            actor.auditActor,
            AuditAction.REPRIMAND_GIVEN,
            "${target.name} gerügt ($count. Rüge, Timeout ${Durations.german(timeoutMinutes)}), Grund: $text",
        )
        return ReprimandOutcome.Reprimanded(requireNotNull(saved.id), count, timeoutMinutes, timeoutUntil)
    }

    /**
     * The bot could not set or lift a timeout. A reprimand whose timeout was never set has none to lift when it is
     * withdrawn, so its end is forgotten.
     */
    @Transactional
    fun timeoutFailed(
        discordUserId: String,
        memberName: String,
        reason: String,
        reprimandId: Long?,
    ) {
        reprimandId
            ?.let { repository.findByIdOrNull(it) }
            ?.takeIf { it.discordUserId == discordUserId }
            ?.timeoutUntil = null
        systemLog.recordProblem(SystemProblem.ReprimandTimeoutFailed(discordUserId, memberName, reason))
    }

    /** The newest first. */
    fun all(): List<Reprimand> = repository.findAll().sortedByDescending { it.id }

    /**
     * The reprimand no longer counts. If its timeout still runs, the member keeps only what other reprimands that count
     * give them. Withdrawing one that is withdrawn already changes nothing.
     *
     * @throws NotFoundException if there is no such reprimand
     */
    @Transactional
    fun withdraw(
        id: Long,
        actor: AuditActor,
    ) {
        val reprimand = repository.findByIdOrNull(id) ?: throw NotFoundException("No reprimand $id")
        if (reprimand.withdrawn) return
        val now = clock.instant()
        reprimand.withdrawnAt = now
        reprimand.withdrawnByName = actor.name
        auditLog.record(
            actor,
            AuditAction.REPRIMAND_WITHDRAWN,
            "Rüge für ${reprimand.memberName} vom ${GERMAN_DATE.format(
                reprimand.createdAt,
            )} zurückgenommen, Grund war: ${reprimand.reason}",
        )
        val ended = reprimand.timeoutUntil?.takeIf { it > now } ?: return
        val remaining =
            repository
                .findByDiscordUserIdOrderById(reprimand.discordUserId)
                .filter { !it.withdrawn }
                .mapNotNull { it.timeoutUntil }
                .filter { it > now }
                .maxOrNull()
        if (remaining != null && remaining >= ended) return
        eventPublisher.publish(ReprimandTimeoutEvent(reprimand.discordUserId, remaining?.toEpochMilli(), clock.millis()))
    }

    companion object {
        /** As the command allows; the column leaves no more room. */
        const val MAX_REASON_LENGTH = 500

        private val GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Berlin"))
    }
}

/** A member on either side of `/reprimand`, as the bot reported them. */
data class ReprimandParty(
    val discordUserId: String,
    val name: String,
    val avatarUrl: String?,
    val roleIds: List<String>,
    val administrator: Boolean,
    val bot: Boolean,
) {
    val auditActor get() = AuditActor("discord:$discordUserId", name, avatarUrl)
}

sealed interface ReprimandOutcome {
    data class Reprimanded(
        val reprimandId: Long,
        val count: Int,
        val timeoutMinutes: Int,
        val timeoutUntil: Instant,
    ) : ReprimandOutcome

    data object Forbidden : ReprimandOutcome

    data class Protected(
        val reason: ProtectedReason,
    ) : ReprimandOutcome

    data object Disabled : ReprimandOutcome
}

enum class ProtectedReason {
    SELF,
    BOT,
    ADMIN,
    REPRIMANDER,
}
