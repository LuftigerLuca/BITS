package eu.luftiger.bits.backend.reprimand.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A reprimand given with `/reprimand`. It counts until an admin withdraws it; names are as Discord showed them then. */
@Entity
@Table(name = "reprimand")
class Reprimand(
    @Column(name = "discord_user_id", nullable = false)
    val discordUserId: String,
    @Column(name = "member_name", nullable = false)
    val memberName: String,
    @Column(name = "reason", nullable = false)
    val reason: String,
    @Column(name = "actor_discord_user_id", nullable = false)
    val actorDiscordUserId: String,
    @Column(name = "actor_name", nullable = false)
    val actorName: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    /** End of the timeout it brought; `null` once the bot reported that it could not set it. */
    @Column(name = "timeout_until")
    var timeoutUntil: Instant?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null

    @Column(name = "withdrawn_by_name")
    var withdrawnByName: String? = null

    val withdrawn: Boolean get() = withdrawnAt != null
}
