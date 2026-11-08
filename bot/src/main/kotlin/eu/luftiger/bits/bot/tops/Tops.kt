package eu.luftiger.bits.bot.tops

import eu.luftiger.bits.bot.backend.TopBackend
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.TopAction
import eu.luftiger.bits.shared.TopActionReport
import eu.luftiger.bits.shared.TopActor
import eu.luftiger.bits.shared.TopPermissionResponse
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import org.slf4j.LoggerFactory

/**
 * Closes and reopens agenda items (TOPs): posts in the forums an admin picked. The backend decides whether the member
 * may and which tag a closed TOP gets; the bot does the rest in Discord and then reports it for the audit log. Each
 * function returns the reply for the member, in [Language]; a backend that cannot be reached throws.
 */
class Tops(
    private val backend: TopBackend,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Posts the closing message, sets the done tag, locks the post and, with [archive], archives it. */
    suspend fun close(
        post: TopPost,
        actor: TopActor,
        mention: String,
        reason: String?,
        archive: Boolean,
        language: Language,
    ): String {
        val doneTagId =
            when (val permission = backend.topPermission(actor, post.forumId)) {
                is TopPermissionResponse.Allowed -> permission.doneTagId
                TopPermissionResponse.Forbidden -> return forbiddenMessage(language)
                TopPermissionResponse.NotTopForum -> return notInTopMessage(language)
            }
        if (post.locked) return language.text("Dieser TOP ist schon geschlossen.", "This agenda item is closed already.")
        if (!post.botMayManage) return missingPermissionsMessage(language)
        val tag = doneTagId?.takeIf { it in post.forumTagIds && it !in post.appliedTagIds }
        val tagFits = tag == null || post.appliedTagIds.size < ForumChannel.MAX_POST_TAGS
        val tagIds = if (tag != null && tagFits) post.appliedTagIds + tag else post.appliedTagIds
        val reasonText = reason?.trim()?.ifEmpty { null }
        val done =
            discord(post, "close") {
                post.send(TopMessages.closed(mention, reasonText))
                post.update(tagIds, locked = true, archived = archive)
            }
        if (!done) return discordFailedMessage(language)
        report(TopActionReport(actor, post.forumId, post.id, post.name, TopAction.CLOSED, reasonText, archive))
        val closed =
            if (archive) {
                language.text("Der TOP ist geschlossen und archiviert.", "The agenda item is closed and archived.")
            } else {
                language.text("Der TOP ist geschlossen.", "The agenda item is closed.")
            }
        if (tagFits) return closed
        return closed + " " +
            language.text(
                "Das Erledigt-Tag fehlt, da der Post schon ${ForumChannel.MAX_POST_TAGS} Tags hat.",
                "The done tag is missing, since the post has ${ForumChannel.MAX_POST_TAGS} tags already.",
            )
    }

    /** Unarchives and unlocks the post, removes the done tag and posts that it is open again. */
    suspend fun reopen(
        post: TopPost,
        actor: TopActor,
        mention: String,
        language: Language,
    ): String {
        val doneTagId =
            when (val permission = backend.topPermission(actor, post.forumId)) {
                is TopPermissionResponse.Allowed -> permission.doneTagId
                TopPermissionResponse.Forbidden -> return forbiddenMessage(language)
                TopPermissionResponse.NotTopForum -> return notInTopMessage(language)
            }
        if (!post.locked) return language.text("Dieser TOP ist nicht geschlossen.", "This agenda item is not closed.")
        if (!post.botMayManage) return missingPermissionsMessage(language)
        val done =
            discord(post, "reopen") {
                // An archived post takes no message, so it is opened first.
                post.update(post.appliedTagIds - setOfNotNull(doneTagId), locked = false, archived = false)
                post.send(TopMessages.reopened(mention))
            }
        if (!done) return discordFailedMessage(language)
        report(TopActionReport(actor, post.forumId, post.id, post.name, TopAction.REOPENED))
        return language.text("Der TOP ist wieder offen.", "The agenda item is open again.")
    }

    private suspend fun discord(
        post: TopPost,
        what: String,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not {} TOP post {} in Discord", what, post.id, e)
            false
        }

    /** The post is changed already, so a backend that does not take the report costs only the audit entry. */
    private suspend fun report(report: TopActionReport) {
        try {
            backend.reportTopAction(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Could not report {} of TOP post {} for the audit log", report.action, report.threadId, e)
        }
    }

    companion object {
        fun notInTopMessage(language: Language): String =
            language.text(
                "Das geht nur in einem Post eines TOP-Forums.",
                "This only works in a post of an agenda forum.",
            )

        private fun forbiddenMessage(language: Language): String =
            language.text(
                "TOPs schließen und wieder öffnen dürfen nur Admins und Mitglieder der Rollen, die im Dashboard für " +
                    "TOPs freigegeben sind.",
                "Only admins and members of the roles allowed to manage agenda items in the dashboard may close and reopen them.",
            )

        private fun missingPermissionsMessage(language: Language): String =
            language.text(
                "In diesem Forum darf ich keine Posts verwalten. Ich brauche dort die Rechte, Threads zu verwalten, in " +
                    "Threads Nachrichten zu senden und Links einzubetten.",
                "I may not manage posts in this forum. I need the permissions to manage threads, to send messages in " +
                    "threads and to embed links there.",
            )

        private fun discordFailedMessage(language: Language): String =
            language.text(
                "Discord hat das nicht angenommen. Prüfe meine Rechte im Forum und versuche es noch einmal.",
                "Discord did not accept that. Check my permissions in the forum and try again.",
            )
    }
}
