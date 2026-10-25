package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A private thread of the guild as last reported by the bot. */
@Entity
@Table(name = "discord_thread")
class DiscordThread(
    @Id
    @Column(name = "id")
    val id: String,
    @Column(name = "name", nullable = false)
    val name: String,
    @Column(name = "parent_channel_id", nullable = false)
    val parentChannelId: String,
    @Column(name = "parent_channel_name", nullable = false)
    val parentChannelName: String,
    @Column(name = "archived", nullable = false)
    val archived: Boolean,
    /** Whether the bot may add and remove members. */
    @Column(name = "manageable", nullable = false)
    val manageable: Boolean,
)
