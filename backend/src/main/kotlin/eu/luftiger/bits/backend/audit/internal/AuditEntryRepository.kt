package eu.luftiger.bits.backend.audit.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.domain.AuditEntry
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AuditEntryRepository : JpaRepository<AuditEntry, Long> {
    /** Newest first; each filter that is `null` is left out. [before] is the ID of the last entry of the page before. */
    @Query(
        """
        SELECT e FROM AuditEntry e
        WHERE (:actor IS NULL OR e.actorSubject = :actor)
          AND (:action IS NULL OR e.action = :action)
          AND (:before IS NULL OR e.id < :before)
        ORDER BY e.id DESC
        """,
    )
    fun findPage(
        actor: String?,
        action: AuditAction?,
        before: Long?,
        limit: Limit,
    ): List<AuditEntry>

    /** Everybody who appears in the log, former admins too, each with the name and avatar of their latest entry. */
    @Query(
        """
        SELECT DISTINCT ON (actor_subject) actor_subject AS subject, actor_name AS name, actor_avatar_url AS avatarUrl
        FROM audit_entry
        ORDER BY actor_subject, id DESC
        """,
        nativeQuery = true,
    )
    fun findActors(): List<ActorRow>

    interface ActorRow {
        val subject: String
        val name: String
        val avatarUrl: String?
    }
}
