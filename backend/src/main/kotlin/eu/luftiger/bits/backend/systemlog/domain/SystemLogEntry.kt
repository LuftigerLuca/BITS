package eu.luftiger.bits.backend.systemlog.domain

import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * One thing BITS did on its own. Which fields are set depends on [kind]; names are snapshots, see the migration of
 * `system_log_entry`. Only a sync run changes after it was written, once the bot reports back.
 */
@Entity
@Table(name = "system_log_entry")
class SystemLogEntry(
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    val kind: EntryKind,
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_trigger")
    val syncTrigger: SyncTrigger? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    var syncStatus: SyncStatus? = null,
    @Column(name = "linked_count")
    val linkedCount: Int? = null,
    @Column(name = "discord_user_id")
    val discordUserId: String? = null,
    @Column(name = "discord_name")
    val discordName: String? = null,
    @Column(name = "previous_discord_user_id")
    val previousDiscordUserId: String? = null,
    @Column(name = "previous_discord_name")
    val previousDiscordName: String? = null,
    @Column(name = "warera_user_id")
    val wareraUserId: String? = null,
    @Column(name = "warera_username")
    val wareraUsername: String? = null,
    @Column(name = "role_id")
    val roleId: String? = null,
    @Column(name = "role_name")
    val roleName: String? = null,
    @Column(name = "message")
    val message: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "member_count")
    var memberCount: Int? = null

    @Column(name = "stale_member_count")
    var staleMemberCount: Int? = null

    @Column(name = "granted_count")
    var grantedCount: Int? = null

    @Column(name = "removed_count")
    var removedCount: Int? = null

    @Column(name = "skipped_count")
    var skippedCount: Int? = null

    @Column(name = "failed_count")
    var failedCount: Int? = null

    /** Nicknames set or cleared; `null` from a bot before nicknames. */
    @Column(name = "nickname_count")
    var nicknameCount: Int? = null

    @Column(name = "reported_at")
    var reportedAt: Instant? = null
}

enum class EntryKind(
    val category: Category,
) {
    SYNC_RUN(Category.SYNC),

    /** A sync of the embassy threads; the counts are thread and ambassador role changes. */
    EMBASSY_SYNC_RUN(Category.SYNC),
    ACCOUNT_LINKED(Category.ACCOUNT),

    /** A WarEra account moved from one Discord account to another. */
    ACCOUNT_RELINKED(Category.ACCOUNT),

    /** The link of a member who left the guild was deleted. */
    ACCOUNT_REMOVED(Category.ACCOUNT),
    WARERA_USERS_UNAVAILABLE(Category.ERROR),
    GOVERNMENT_UNAVAILABLE(Category.ERROR),

    /** The parties of a country could not be loaded for a sync. */
    PARTIES_UNAVAILABLE(Category.ERROR),

    /** A military unit could not be loaded for a sync. */
    MILITARY_UNIT_UNAVAILABLE(Category.ERROR),
    ROLE_NOT_ASSIGNABLE(Category.ERROR),
    SYNC_ABORTED(Category.ERROR),

    /** The bot gave up on an event and moved it to the dead letter stream. */
    EVENT_UNDELIVERED(Category.ERROR),

    /** Events could not be written to Redis for a while. */
    OUTBOX_STUCK(Category.ERROR),

    /** A member list from the bot would have deleted too many links at once. */
    MEMBER_REPORT_REFUSED(Category.ERROR),

    /** The result of a vote could not be worked out yet. */
    VOTE_EVALUATION_DELAYED(Category.ERROR),

    /** An embassy thread is gone, or the bot may not manage it. */
    EMBASSY_THREAD_UNAVAILABLE(Category.ERROR),

    /** The bot could not create an embassy thread. */
    EMBASSY_THREAD_CREATION_FAILED(Category.ERROR),
    ;

    enum class Category {
        SYNC,
        ACCOUNT,
        ERROR,
    }
}

enum class SyncStatus {
    /** Published; the bot has not reported back yet. */
    PENDING,
    APPLIED,

    /** The bot left every member alone, since newer syncs had set their roles already. */
    STALE,

    /** Nothing was published, since no rule can be run. */
    NO_RULES,
}
