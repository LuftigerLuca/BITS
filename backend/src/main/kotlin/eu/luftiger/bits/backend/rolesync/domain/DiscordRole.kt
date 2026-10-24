package eu.luftiger.bits.backend.rolesync.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A role of the guild as last reported by the bot. */
@Entity
@Table(name = "discord_role")
class DiscordRole(
    @Id
    @Column(name = "id")
    val id: String,
    @Column(name = "name", nullable = false)
    val name: String,
    @Column(name = "color")
    val color: Int?,
    @Column(name = "position", nullable = false)
    val position: Int,
    /** Whether the bot can give and take the role. */
    @Column(name = "assignable", nullable = false)
    val assignable: Boolean,
)
