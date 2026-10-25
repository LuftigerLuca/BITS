package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A text channel of the guild as last reported by the bot. */
@Entity
@Table(name = "discord_channel")
class DiscordChannel(
    @Id
    @Column(name = "id")
    val id: String,
    @Column(name = "name", nullable = false)
    val name: String,
    @Column(name = "category")
    val category: String?,
    @Column(name = "position", nullable = false)
    val position: Int,
    /** Whether the bot may post votes and results there. */
    @Column(name = "postable", nullable = false)
    val postable: Boolean,
)
