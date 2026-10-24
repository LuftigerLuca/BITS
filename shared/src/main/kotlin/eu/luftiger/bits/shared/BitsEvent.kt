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
 * role of [managedRoleIds], except for those listed in [MemberRoles.keepRoleIds].
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
