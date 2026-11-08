package eu.luftiger.bits.backend.top.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A forum whose posts are agenda items (TOPs), which `/topclose` and `/topopen` work in. */
@Entity
@Table(name = "top_forum")
class TopForum(
    @Id
    @Column(name = "forum_id")
    val forumId: String,
    /** The tag a closed TOP gets; `null` for none. */
    @Column(name = "done_tag_id")
    var doneTagId: String?,
)
