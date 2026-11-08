package eu.luftiger.bits.backend.verifypanel.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.shared.NotPossibleInDiscordException
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.backend.verifypanel.domain.VerifyPanelSetting
import eu.luftiger.bits.backend.vote.api.GuildChannels
import eu.luftiger.bits.backend.vote.api.dto.GuildChannel
import eu.luftiger.bits.shared.VerifyPanelMessage
import eu.luftiger.bits.shared.VerifyPanelProblemReport
import eu.luftiger.bits.shared.VerifyPanelReport
import eu.luftiger.bits.shared.VerifyPanelState
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The channel in which the bot keeps a message with a button to verify, the way in for new members. The bot asks for
 * the state periodically and keeps the message itself, so it keeps working with what it knows while the backend is
 * down; the button starts the same flow as `/verify`.
 */
@Service
class VerifyPanelService(
    private val repository: VerifyPanelRepository,
    private val guildChannels: GuildChannels,
    private val auditLog: AuditLog,
    private val systemLog: SystemLog,
    private val clock: Clock,
) {
    fun state(): VerifyPanelState {
        val setting = setting() ?: return VerifyPanelState()
        val messageChannelId = setting.messageChannelId
        val messageId = setting.messageId
        return VerifyPanelState(
            channelId = setting.channelId,
            message = if (messageChannelId != null && messageId != null) VerifyPanelMessage(messageChannelId, messageId) else null,
        )
    }

    /** Text channels in the order of Discord. */
    fun channels(): List<GuildChannel> = guildChannels.all()

    /**
     * Sets the channel, or removes it with `null`.
     *
     * @throws InvalidInputException if there is no such channel
     * @throws NotPossibleInDiscordException if the bot may not post there
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
        if (channel != null && !channel.postable) {
            throw NotPossibleInDiscordException("Bot may not post in channel ${channel.id}")
        }
        val setting = current ?: repository.save(VerifyPanelSetting(null, null, null, clock.instant()))
        setting.channelId = channelId
        setting.updatedAt = clock.instant()
        auditLog.record(
            actor,
            AuditAction.VERIFY_PANEL_CHANNEL_CHANGED,
            if (channel == null) "Verifizierungs-Kanal entfernt" else "Verifizierungs-Kanal: #${channel.name}",
        )
    }

    @Transactional
    fun recordMessage(report: VerifyPanelReport) {
        val setting = setting() ?: repository.save(VerifyPanelSetting(null, null, null, clock.instant()))
        setting.messageChannelId = report.message?.channelId
        setting.messageId = report.message?.messageId
        setting.updatedAt = clock.instant()
    }

    fun recordProblem(report: VerifyPanelProblemReport) = systemLog.recordProblem(SystemProblem.VerifyPanelFailed(report.reason))

    private fun setting(): VerifyPanelSetting? = repository.findByIdOrNull(VerifyPanelSetting.SINGLE_ROW_ID)
}
