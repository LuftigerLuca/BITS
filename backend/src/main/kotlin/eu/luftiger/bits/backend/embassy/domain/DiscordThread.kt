package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
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
    @Column(name = "everyone_can_view", nullable = false)
    val everyoneCanView: Boolean,
    /** Roles that may see the parent channel; empty if [everyoneCanView]. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discord_thread_viewer_role", joinColumns = [JoinColumn(name = "thread_id")])
    @Column(name = "role_id")
    val viewerRoleIds: Set<String>,
) {
    /**
     * Whether a member with [roleIds] may see the parent channel, so Discord lets the bot add them. Member overwrites
     * and roles denied explicitly are not considered; the bot reports what Discord refuses anyway.
     */
    fun visibleWith(roleIds: Collection<String>) = everyoneCanView || roleIds.any { it in viewerRoleIds }
}
