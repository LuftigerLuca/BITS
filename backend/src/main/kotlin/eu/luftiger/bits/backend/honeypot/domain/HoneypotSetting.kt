package eu.luftiger.bits.backend.honeypot.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The honeypot channel and the bot's warning in it; there is at most one row. */
@Entity
@Table(name = "honeypot_setting")
class HoneypotSetting(
    /** `null` while no honeypot is set. */
    @Column(name = "channel_id")
    var channelId: String?,
    /** Where the warning is; differs from [channelId] until the bot has moved it. */
    @Column(name = "warning_channel_id")
    var warningChannelId: String?,
    @Column(name = "warning_message_id")
    var warningMessageId: String?,
    @Column(name = "kicks_paused", nullable = false)
    var kicksPaused: Boolean,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    @Id
    @Column(name = "id")
    val id: Short = SINGLE_ROW_ID

    companion object {
        const val SINGLE_ROW_ID: Short = 1
    }
}
