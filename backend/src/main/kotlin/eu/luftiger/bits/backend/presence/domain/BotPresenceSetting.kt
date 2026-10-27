package eu.luftiger.bits.backend.presence.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Status and activity of the bot in Discord; there is at most one row, written once an admin sets them. */
@Entity
@Table(name = "bot_presence_setting")
class BotPresenceSetting(
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PresenceStatus,
    /** `null` for no activity, together with [activityText]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type")
    var activityType: ActivityType?,
    @Column(name = "activity_text")
    var activityText: String?,
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

/** Stored by name, so existing names stay as they are. */
enum class PresenceStatus {
    ONLINE,
    IDLE,
    DO_NOT_DISTURB,
    INVISIBLE,
}

/** Stored by name, so existing names stay as they are. */
enum class ActivityType {
    PLAYING,
    WATCHING,
    LISTENING,
    COMPETING,
    CUSTOM,
}
