package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

/** A member of an embassy thread as the bot reported after the last sync, with why they are in it. */
@Entity
@Immutable
@Table(name = "embassy_thread_member")
class EmbassyThreadMember(
    @Column(name = "thread_id", nullable = false)
    val threadId: String,
    @Column(name = "discord_user_id", nullable = false)
    val discordUserId: String,
    @Column(name = "name", nullable = false)
    val name: String,
    /** German, e.g. "Außenminister von Polen". */
    @Column(name = "reason", nullable = false)
    val reason: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
