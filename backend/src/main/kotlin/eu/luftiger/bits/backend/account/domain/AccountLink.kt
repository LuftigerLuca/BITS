package eu.luftiger.bits.backend.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Verified ownership of a WarEra account by a Discord account. */
@Entity
@Table(name = "account_link")
class AccountLink(
    @Id
    @Column(name = "discord_user_id")
    val discordUserId: String,
    @Column(name = "warera_user_id", nullable = false)
    val wareraUserId: String,
    /** Username at the time of linking; WarEra users can rename themselves. */
    @Column(name = "warera_username", nullable = false)
    val wareraUsername: String,
    @Column(name = "verified_at", nullable = false)
    val verifiedAt: Instant,
)
