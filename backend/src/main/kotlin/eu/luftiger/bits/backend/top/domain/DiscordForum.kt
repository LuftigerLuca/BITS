package eu.luftiger.bits.backend.top.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

/** A forum channel of the guild as last reported by the bot. */
@Entity
@Table(name = "discord_forum")
class DiscordForum(
    @Id
    @Column(name = "id")
    val id: String,
    @Column(name = "name", nullable = false)
    val name: String,
    @Column(name = "category")
    val category: String?,
    @Column(name = "position", nullable = false)
    val position: Int,
    /** Whether the bot may lock, archive and tag the posts there and write in them. */
    @Column(name = "threads_manageable", nullable = false)
    val threadsManageable: Boolean,
    /** In the order Discord shows them. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discord_forum_tag", joinColumns = [JoinColumn(name = "forum_id")])
    @OrderColumn(name = "position")
    val tags: List<ForumTag>,
)

/** A tag that posts of a forum can carry. */
@Embeddable
class ForumTag(
    @Column(name = "tag_id", nullable = false)
    val id: String,
    @Column(name = "name", nullable = false)
    val name: String,
)
