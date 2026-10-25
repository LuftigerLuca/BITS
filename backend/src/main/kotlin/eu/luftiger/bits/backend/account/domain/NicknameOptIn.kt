package eu.luftiger.bits.backend.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A member who wants their WarEra name as nickname; independent of the link, so it lasts across new links. */
@Entity
@Table(name = "nickname_opt_in")
class NicknameOptIn(
    @Id
    @Column(name = "discord_user_id")
    val discordUserId: String,
    @Column(name = "opted_in_at", nullable = false)
    val optedInAt: Instant,
)
