package eu.luftiger.bits.backend.selfservice.internal

import kotlinx.serialization.Serializable

/**
 * Everything BITS keeps in its database about one Discord account, for the member's own download. Times are ISO-8601
 * in UTC. Redis events, application logs and backups are not part of it, see "Datenschutz" in the README.
 */
@Serializable
data class DataExport(
    val discordUserId: String,
    val exportedAt: String,
    val link: ExportedLink?,
    val openVerification: ExportedVerification?,
    val nicknameOptedInAt: String?,
    val pings: List<String>,
    val embassies: ExportedEmbassies,
    val systemLog: ExportedSystemLog,
    /** What the member changed as admin in the dashboard. */
    val auditLog: List<ExportedAuditEntry>,
)

@Serializable
data class ExportedLink(
    val wareraUserId: String,
    val wareraUsername: String,
    val verifiedAt: String,
)

@Serializable
data class ExportedVerification(
    val wareraUserId: String,
    val wareraUsername: String,
    val code: String,
    val expiresAt: String,
)

@Serializable
data class ExportedEmbassies(
    /** Embassy threads an admin added the member to by hand. */
    val addedByHand: List<ExportedManualMembership>,
    /** Embassy threads the bot last reported the member in, with why. */
    val reportedIn: List<ExportedThreadMembership>,
)

@Serializable
data class ExportedManualMembership(
    val countryName: String,
    val threadName: String,
    val memberName: String?,
    val addedAt: String,
)

@Serializable
data class ExportedThreadMembership(
    val countryName: String,
    val threadName: String,
    val memberName: String,
    val reason: String,
)

@Serializable
data class ExportedSystemLog(
    val entries: List<ExportedLogEntry>,
    val items: List<ExportedLogItem>,
)

@Serializable
data class ExportedLogEntry(
    val occurredAt: String,
    val kind: String,
    val discordName: String?,
    val wareraUserId: String?,
    val wareraUsername: String?,
    /** Whether the WarEra account was taken from the member by another Discord account. */
    val previousAccount: Boolean,
)

@Serializable
data class ExportedLogItem(
    val occurredAt: String,
    val entryKind: String,
    val syncTrigger: String?,
    val discordName: String?,
    val roleId: String?,
    val roleName: String?,
    val outcome: String,
    val reason: String?,
)

@Serializable
data class ExportedAuditEntry(
    val occurredAt: String,
    val actorName: String,
    val actorAvatarUrl: String?,
    val action: String,
    val description: String,
)
