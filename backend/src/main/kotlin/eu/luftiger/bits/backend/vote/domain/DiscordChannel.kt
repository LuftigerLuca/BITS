package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
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
    /** Whether the bot may create embassy threads there. */
    @Column(name = "threads_creatable", nullable = false)
    val threadsCreatable: Boolean,
    @Column(name = "everyone_can_view", nullable = false)
    val everyoneCanView: Boolean,
    /** Roles that may see the channel; empty if [everyoneCanView]. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discord_channel_viewer_role", joinColumns = [JoinColumn(name = "channel_id")])
    @Column(name = "role_id")
    val viewerRoleIds: Set<String>,
)
