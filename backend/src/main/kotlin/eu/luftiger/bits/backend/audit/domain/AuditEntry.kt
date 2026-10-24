package eu.luftiger.bits.backend.audit.domain

import eu.luftiger.bits.backend.audit.api.AuditAction
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import java.time.Instant

/** One change in the dashboard. Name, avatar and description are snapshots, see the migration of `audit_entry`. */
@Entity
@Immutable
@Table(name = "audit_entry")
class AuditEntry(
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
    @Column(name = "actor_subject", nullable = false)
    val actorSubject: String,
    @Column(name = "actor_name", nullable = false)
    val actorName: String,
    @Column(name = "actor_avatar_url")
    val actorAvatarUrl: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    val action: AuditAction,
    @Column(name = "description", nullable = false)
    val description: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
