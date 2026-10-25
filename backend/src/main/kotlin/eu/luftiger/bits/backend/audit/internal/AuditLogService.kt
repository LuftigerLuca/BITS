package eu.luftiger.bits.backend.audit.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditEntries
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.audit.api.dto.AuditRecord
import eu.luftiger.bits.backend.audit.domain.AuditEntry
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AuditLogService(
    private val repository: AuditEntryRepository,
    private val clock: Clock,
) : AuditLog,
    AuditEntries {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun record(
        actor: AuditActor,
        action: AuditAction,
        description: String,
    ) {
        repository.save(AuditEntry(clock.instant(), actor.subject, actor.name, actor.avatarUrl, action, description))
    }

    override fun byActor(actorSubject: String): List<AuditRecord> =
        repository.findByActorSubjectOrderById(actorSubject).map {
            AuditRecord(it.occurredAt, it.actorName, it.actorAvatarUrl, it.action, it.description)
        }

    fun page(
        actor: String?,
        action: AuditAction?,
        before: Long?,
    ): AuditLogPage {
        // One more than a page tells whether another page follows.
        val entries = repository.findPage(actor, action, before, Limit.of(PAGE_SIZE + 1))
        val page = entries.take(PAGE_SIZE)
        return AuditLogPage(
            entries = page.map(::response),
            nextBefore = if (entries.size > PAGE_SIZE) page.last().id else null,
        )
    }

    fun actors(): List<AuditActorResponse> =
        repository
            .findActors()
            .map { actorResponse(it.subject, it.name, it.avatarUrl) }
            .sortedBy { it.name.lowercase() }

    private fun response(entry: AuditEntry) =
        AuditEntryResponse(
            id = requireNotNull(entry.id),
            occurredAt = entry.occurredAt.toEpochMilli(),
            actor = actorResponse(entry.actorSubject, entry.actorName, entry.actorAvatarUrl),
            action = entry.action,
            description = entry.description,
        )

    private fun actorResponse(
        subject: String,
        name: String,
        avatarUrl: String?,
    ) = AuditActorResponse(
        subject = subject,
        name = name,
        avatarUrl = avatarUrl,
        kind = if (subject.startsWith(ADMIN_ACCOUNT_PREFIX)) ActorKind.ADMIN_ACCOUNT else ActorKind.DISCORD,
    )

    companion object {
        const val PAGE_SIZE = 50

        /** The subject of the admin account from the environment, see `SessionUser` in the auth module. */
        private const val ADMIN_ACCOUNT_PREFIX = "admin:"
    }
}
