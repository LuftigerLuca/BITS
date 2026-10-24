package eu.luftiger.bits.backend.systemlog.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

/** A role change of a sync run, or a member an error is about; belongs to the entry [entryId]. */
@Entity
@Immutable
@Table(name = "system_log_item")
class SystemLogItem(
    @Column(name = "entry_id", nullable = false)
    val entryId: Long,
    @Column(name = "discord_user_id", nullable = false)
    val discordUserId: String,
    @Column(name = "discord_name")
    val discordName: String?,
    @Column(name = "role_id")
    val roleId: String?,
    @Column(name = "role_name")
    val roleName: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    val outcome: ItemOutcome,
    @Column(name = "reason")
    val reason: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}

enum class ItemOutcome {
    GRANTED,
    REMOVED,
    SKIPPED,
    FAILED,
}
