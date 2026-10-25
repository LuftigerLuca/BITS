package eu.luftiger.bits.backend.systemlog.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

/** The system log as far as it is about one member, oldest first. */
@NamedInterface("api")
data class MemberLogRecords(
    val entries: List<MemberLogEntry>,
    val items: List<MemberLogItem>,
)

/** An entry about the member's account, such as a link. */
@NamedInterface("api")
data class MemberLogEntry(
    val occurredAt: Instant,
    /** Name of the kind of entry, e.g. `ACCOUNT_LINKED`. */
    val kind: String,
    /** The member's name at the time. */
    val discordName: String?,
    val wareraUserId: String?,
    val wareraUsername: String?,
    /** Whether the member is the account the WarEra account was taken from, rather than the one it moved to. */
    val previousAccount: Boolean,
)

/** A role or nickname change of a sync, or a problem the member was part of. */
@NamedInterface("api")
data class MemberLogItem(
    val occurredAt: Instant,
    /** Name of the kind of the entry it belongs to, e.g. `SYNC_RUN`. */
    val entryKind: String,
    /** Name of the trigger of the sync, e.g. `HOURLY`; `null` if the entry is no sync. */
    val syncTrigger: String?,
    val discordName: String?,
    val roleId: String?,
    val roleName: String?,
    /** Name of the outcome, e.g. `GRANTED`. */
    val outcome: String,
    val reason: String?,
)
