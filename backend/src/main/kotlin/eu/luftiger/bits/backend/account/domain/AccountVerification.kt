package eu.luftiger.bits.backend.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Open code a Discord user has to put into the name of one of their WarEra companies. */
@Entity
@Table(name = "account_verification")
class AccountVerification(
    @Id
    @Column(name = "discord_user_id")
    val discordUserId: String,
    @Column(name = "warera_user_id", nullable = false)
    val wareraUserId: String,
    @Column(name = "warera_username", nullable = false)
    val wareraUsername: String,
    @Column(name = "code", nullable = false)
    val code: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Column(name = "last_checked_at")
    var lastCheckedAt: Instant? = null
}
