package eu.luftiger.bits.backend.top.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.shared.NotPossibleInDiscordException
import eu.luftiger.bits.backend.top.domain.DiscordForum
import eu.luftiger.bits.backend.top.domain.ForumTag
import eu.luftiger.bits.backend.top.domain.TopForum
import eu.luftiger.bits.backend.vote.api.VoteManagers
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The agenda items (TOPs) in the forums an admin picked. The bot asks whether a member may close or reopen a post,
 * does that in Discord itself and then reports it here for the audit log. Who may is the same as for managing votes.
 */
@Service
class TopService(
    private val forumRepository: DiscordForumRepository,
    private val topForumRepository: TopForumRepository,
    private val voteManagers: VoteManagers,
    private val auditLog: AuditLog,
) {
    @Transactional
    fun replaceForums(forums: List<DiscordForum>) {
        forumRepository.deleteAllInBatch()
        forumRepository.saveAll(forums)
    }

    /** In the order Discord lists them. */
    fun forums(): List<DiscordForum> = forumRepository.findAll().sortedBy { it.position }

    fun topForums(): List<TopForum> = topForumRepository.findAll().sortedBy { it.forumId }

    fun permission(
        actor: TopActor,
        forumId: String,
    ): TopPermission {
        val forum = topForumRepository.findById(forumId).orElse(null) ?: return TopPermission.NotTopForum
        if (!voteManagers.mayManage(actor.admin, actor.roleIds)) return TopPermission.Forbidden
        return TopPermission.Allowed(forum.doneTagId)
    }

    @Transactional
    fun record(
        actor: TopActor,
        forumId: String,
        threadName: String,
        action: TopRecord,
    ) {
        val forum = forumRepository.findById(forumId).orElse(null)?.let { "#${it.name}" } ?: "einem Forum"
        val post = "TOP „$threadName“ in $forum"
        when (action) {
            is TopRecord.Closed -> {
                val details =
                    listOfNotNull(
                        if (action.archived) "und archiviert" else null,
                        action.reason?.let { "Grund: $it" },
                    )
                val description = "$post geschlossen" + if (details.isEmpty()) "" else " ${details.joinToString(", ")}"
                auditLog.record(actor.auditActor, AuditAction.TOP_CLOSED, description)
            }
            TopRecord.Reopened -> auditLog.record(actor.auditActor, AuditAction.TOP_REOPENED, "$post wieder geöffnet")
        }
    }

    /**
     * Replaces the TOP forums. Saving what is set already records nothing.
     *
     * @throws InvalidInputException if a forum or a tag is unknown, or a forum comes twice
     * @throws NotPossibleInDiscordException if the bot may not manage the posts of a newly added forum
     */
    @Transactional
    fun setTopForums(
        selections: List<TopForumSelection>,
        actor: AuditActor,
    ) {
        if (selections.map { it.forumId }.toSet().size != selections.size) throw InvalidInputException("A forum is listed twice")
        val forums = forumRepository.findAll().associateBy { it.id }
        val existing = topForumRepository.findAll().associateBy { it.forumId }
        val current = existing.mapValues { (_, forum) -> forum.doneTagId }
        selections.forEach { selection ->
            val forum = forums[selection.forumId] ?: throw InvalidInputException("Unknown forum ${selection.forumId}")
            if (selection.forumId !in current && !forum.threadsManageable) {
                throw NotPossibleInDiscordException("Bot may not manage the posts of forum ${forum.id}")
            }
            if (selection.doneTagId != null && forum.tags.none { it.id == selection.doneTagId }) {
                throw InvalidInputException("Unknown tag ${selection.doneTagId} in forum ${forum.id}")
            }
        }
        if (selections.associate { it.forumId to it.doneTagId } == current) return
        val selected = selections.mapTo(HashSet()) { it.forumId }
        topForumRepository.deleteAll(existing.values.filter { it.forumId !in selected })
        selections.forEach { selection ->
            val forum = existing[selection.forumId]
            if (forum ==
                null
            ) {
                topForumRepository.save(TopForum(selection.forumId, selection.doneTagId))
            } else {
                forum.doneTagId = selection.doneTagId
            }
        }
        auditLog.record(actor, AuditAction.TOP_FORUMS_CHANGED, describe(selections, forums))
    }

    private fun describe(
        selections: List<TopForumSelection>,
        forums: Map<String, DiscordForum>,
    ): String {
        if (selections.isEmpty()) return "TOP-Foren: keine"
        val names =
            selections
                .map { selection ->
                    val forum = forums.getValue(selection.forumId)
                    val tag = selection.doneTagId?.let { id -> forum.tags.first { it.id == id } }
                    forum to tag
                }.sortedBy { (forum) -> forum.position }
                .joinToString { (forum, tag: ForumTag?) -> "#${forum.name}" + (tag?.let { " (Erledigt-Tag: ${it.name})" } ?: "") }
        return "TOP-Foren: $names"
    }
}

/** A member in Discord who closes or reopens a TOP. */
data class TopActor(
    val auditActor: AuditActor,
    val admin: Boolean,
    val roleIds: List<String>,
)

sealed interface TopPermission {
    data class Allowed(
        val doneTagId: String?,
    ) : TopPermission

    data object Forbidden : TopPermission

    data object NotTopForum : TopPermission
}

sealed interface TopRecord {
    data class Closed(
        val reason: String?,
        val archived: Boolean,
    ) : TopRecord

    data object Reopened : TopRecord
}

data class TopForumSelection(
    val forumId: String,
    val doneTagId: String?,
)
