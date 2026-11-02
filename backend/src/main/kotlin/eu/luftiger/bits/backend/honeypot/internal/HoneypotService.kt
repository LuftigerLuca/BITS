package eu.luftiger.bits.backend.honeypot.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.honeypot.domain.HoneypotSetting
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.shared.NotPossibleInDiscordException
import eu.luftiger.bits.backend.systemlog.api.HoneypotAction
import eu.luftiger.bits.backend.systemlog.api.HoneypotActionOutcome
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.backend.vote.api.GuildChannels
import eu.luftiger.bits.backend.vote.api.dto.GuildChannel
import eu.luftiger.bits.shared.HoneypotKickOutcome
import eu.luftiger.bits.shared.HoneypotKickReport
import eu.luftiger.bits.shared.HoneypotPauseReport
import eu.luftiger.bits.shared.HoneypotProblemReport
import eu.luftiger.bits.shared.HoneypotState
import eu.luftiger.bits.shared.HoneypotWarning
import eu.luftiger.bits.shared.HoneypotWarningReport
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The channel in which writing gets a member kicked. The bot asks for the state periodically and does the rest itself,
 * so it keeps working with what it knows while the backend is down; it counts the kicks and reports when it paused them.
 */
@Service
class HoneypotService(
    private val repository: HoneypotRepository,
    private val guildChannels: GuildChannels,
    private val auditLog: AuditLog,
    private val systemLog: SystemLog,
    private val clock: Clock,
) {
    fun state(): HoneypotState {
        val setting = setting() ?: return HoneypotState()
        val warningChannelId = setting.warningChannelId
        val warningMessageId = setting.warningMessageId
        return HoneypotState(
            channelId = setting.channelId,
            warning =
                if (warningChannelId != null &&
                    warningMessageId != null
                ) {
                    HoneypotWarning(warningChannelId, warningMessageId)
                } else {
                    null
                },
            kicksPaused = setting.kicksPaused,
        )
    }

    /** Text channels in the order of Discord. */
    fun channels(): List<GuildChannel> = guildChannels.all()

    /**
     * Sets the honeypot, or removes it with `null`. A new choice also lifts a pause, which was about the previous one.
     *
     * @throws InvalidInputException if there is no such channel
     * @throws NotPossibleInDiscordException if the bot lacks the permissions a honeypot takes
     */
    @Transactional
    fun setChannel(
        channelId: String?,
        actor: AuditActor,
    ) {
        val current = setting()
        if (current?.channelId == channelId) return
        val channel =
            channelId?.let { id ->
                guildChannels.all().firstOrNull { it.id == id } ?: throw InvalidInputException("Unknown channel $id")
            }
        if (channel != null && !channel.honeypotUsable) {
            throw NotPossibleInDiscordException("Bot may not watch channel ${channel.id} as a honeypot")
        }
        val setting = current ?: repository.save(HoneypotSetting(null, null, null, false, clock.instant()))
        setting.channelId = channelId
        setting.kicksPaused = false
        setting.updatedAt = clock.instant()
        auditLog.record(
            actor,
            AuditAction.HONEYPOT_CHANNEL_CHANGED,
            if (channel == null) "Honeypot-Kanal entfernt" else "Honeypot-Kanal: #${channel.name}",
        )
    }

    /** Lets the bot kick again; changes nothing if kicks are not paused. */
    @Transactional
    fun resumeKicks(actor: AuditActor) {
        val setting = setting()?.takeIf { it.kicksPaused } ?: return
        setting.kicksPaused = false
        setting.updatedAt = clock.instant()
        auditLog.record(actor, AuditAction.HONEYPOT_KICKS_RESUMED, "Honeypot-Kicks fortgesetzt")
    }

    @Transactional
    fun recordWarning(report: HoneypotWarningReport) {
        val setting = setting() ?: repository.save(HoneypotSetting(null, null, null, false, clock.instant()))
        setting.warningChannelId = report.warning?.channelId
        setting.warningMessageId = report.warning?.messageId
        setting.updatedAt = clock.instant()
    }

    @Transactional
    fun pauseKicks(report: HoneypotPauseReport) {
        val setting = setting()?.takeIf { it.channelId == report.channelId && !it.kicksPaused } ?: return
        setting.kicksPaused = true
        setting.updatedAt = clock.instant()
        systemLog.recordProblem(SystemProblem.HoneypotKicksPaused(report.kickCount, report.windowMinutes))
    }

    fun recordKick(report: HoneypotKickReport) {
        systemLog.recordHoneypotAction(
            HoneypotAction(
                report.discordUserId,
                report.memberName,
                when (report.outcome) {
                    HoneypotKickOutcome.KICKED -> HoneypotActionOutcome.KICKED
                    HoneypotKickOutcome.SKIPPED -> HoneypotActionOutcome.SKIPPED
                    HoneypotKickOutcome.FAILED -> HoneypotActionOutcome.FAILED
                },
                report.reason,
            ),
        )
    }

    fun recordProblem(report: HoneypotProblemReport) = systemLog.recordProblem(SystemProblem.HoneypotWarningFailed(report.reason))

    private fun setting(): HoneypotSetting? = repository.findByIdOrNull(HoneypotSetting.SINGLE_ROW_ID)
}
