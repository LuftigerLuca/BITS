package eu.luftiger.bits.backend.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The language a member chose for what BITS writes only to them; independent of the link. */
@Entity
@Table(name = "language_preference")
class LanguagePreference(
    @Id
    @Column(name = "discord_user_id")
    val discordUserId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    var language: PreferredLanguage,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
)

enum class PreferredLanguage {
    GERMAN,
    ENGLISH,
}
