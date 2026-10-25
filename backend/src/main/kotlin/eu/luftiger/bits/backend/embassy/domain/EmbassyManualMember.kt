package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A member an admin added to an embassy thread; stays in it regardless of any office until an admin removes them. */
@Entity
@Table(name = "embassy_manual_member")
class EmbassyManualMember(
    @Column(name = "thread_id", nullable = false)
    val threadId: String,
    @Column(name = "discord_user_id", nullable = false)
    val discordUserId: String,
    /** As the dashboard showed the member when they were added; `null` if it did not know them. */
    @Column(name = "member_name")
    val memberName: String?,
    @Column(name = "added_at", nullable = false)
    val addedAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
