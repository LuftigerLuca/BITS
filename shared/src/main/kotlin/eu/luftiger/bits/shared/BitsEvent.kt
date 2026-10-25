package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Event the backend publishes for the bot. Each subtype is identified by its `type` field. */
@Serializable
sealed interface BitsEvent {
    /** Epoch milliseconds at which the event happened. */
    val occurredAt: Long
}

/** Example event: published whenever the backend handles a ping. */
@Serializable
@SerialName("ping")
data class PingEvent(
    val discordUserId: String,
    override val occurredAt: Long,
) : BitsEvent

/** Published when a Discord account was linked to a WarEra account. */
@Serializable
@SerialName("account_linked")
data class AccountLinkedEvent(
    val discordUserId: String,
    val wareraUserId: String,
    /** Discord account the WarEra account was linked to before, if it was another one. */
    val previousDiscordUserId: String?,
    override val occurredAt: Long,
    /** WarEra username at the time of linking, for the message to the previous Discord account. */
    val wareraUsername: String? = null,
) : BitsEvent

/** Result of a verification check that answered [CheckVerificationResponse.Checking]. */
@Serializable
@SerialName("verification_checked")
data class VerificationCheckedEvent(
    val discordUserId: String,
    val code: String,
    /** [CheckVerificationResponse.Linked], [CheckVerificationResponse.CodeMissing] or another final outcome. */
    val result: CheckVerificationResponse,
    override val occurredAt: Long,
) : BitsEvent

/**
 * Target state of the roles BITS manages. The bot gives each member the roles in [members] and takes away every other
 * role of [managedRoleIds], except for those listed in [MemberRoles.keepRoleIds]. It also sets the nicknames of members
 * who opted in to their WarEra names, see [nicknames].
 */
@Serializable
@SerialName("role_sync")
data class RoleSyncEvent(
    /** Roles that have at least one rule; nobody keeps them without a matching rule. */
    val managedRoleIds: List<String>,
    /** Members to sync, or `null` for all members of the guild. */
    val discordUserIds: List<String>?,
    /** Target roles by Discord user ID. Members without an entry get none of the managed roles. */
    val members: Map<String, MemberRoles>,
    override val occurredAt: Long,
    /**
     * Roles whose rules an admin changed since the last sync. When a sync of all members would take a role from a large
     * share of its holders, the bot holds back, since that is more likely missing WarEra data than real change; for
     * these roles, the change is what the admin asked for.
     */
    val confirmedRoleIds: List<String> = emptyList(),
    /**
     * Run in the backend's system log that this sync belongs to; the bot reports what it changed under this ID, see
     * [RoleSyncReport]. `null` from a backend before the system log.
     */
    val syncId: Long? = null,
    /**
     * WarEra name by Discord user ID of the members who opted in, which the bot sets as nickname; `null` from a backend
     * before nicknames. Members without an entry keep theirs, e.g. because they did not opt in or their WarEra data
     * could not be loaded.
     */
    val nicknames: Map<String, String>? = null,
    /**
     * Members of [discordUserIds] who lost their link, with the WarEra name they had. The bot clears the nickname if it
     * is still that name; one the member chose stays. Only members who opted in.
     */
    val nicknameResets: Map<String, String> = emptyMap(),
) : BitsEvent

@Serializable
data class MemberRoles(
    val grants: List<RoleGrant>,
    /** Managed roles that stay as they are, because the WarEra data needed to decide on them could not be loaded. */
    val keepRoleIds: List<String> = emptyList(),
)

@Serializable
data class RoleGrant(
    val roleId: String,
    /** Shown in Discord's audit log. */
    val reason: String,
)

fun BitsEvent.encode(): String = BitsJson.encodeToString(BitsEvent.serializer(), this)

fun decodeEvent(payload: String): BitsEvent = BitsJson.decodeFromString(BitsEvent.serializer(), payload)
