package eu.luftiger.bits.backend.verifypanel.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The channel with the button to verify and the bot's message in it; there is at most one row. */
@Entity
@Table(name = "verify_panel_setting")
class VerifyPanelSetting(
    /** `null` while no channel is set. */
    @Column(name = "channel_id")
    var channelId: String?,
    /** Where the message is; differs from [channelId] until the bot has moved it. */
    @Column(name = "message_channel_id")
    var messageChannelId: String?,
    @Column(name = "message_id")
    var messageId: String?,
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
