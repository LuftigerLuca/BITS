package eu.luftiger.bits.backend.memberadmin.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.account.api.AccountSelfService
import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.members.api.GuildMembers
import eu.luftiger.bits.backend.rolesync.api.RoleExplanations
import eu.luftiger.bits.backend.rolesync.api.RoleSyncRequests
import eu.luftiger.bits.backend.systemlog.api.SystemLogRecords
import eu.luftiger.bits.shared.ManagedRoleOutcome
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

/**
 * The page "Mitglieder", where admins look up a member who asks about their roles: the link, the managed roles with
 * the reasons, what the system log keeps about them, and two actions, a sync and unlinking, each recorded in the
 * audit log.
 *
 * A member is known if the bot reported them on the guild or they have a link; a linked member who left keeps showing
 * until the link is deleted. Discord names come from the bot's last report, WarEra names from the time of linking.
 */
@Service
class MemberAdminService(
    private val guildMembers: GuildMembers,
    private val accountLinks: AccountLinks,
    private val accounts: AccountSelfService,
    private val roleExplanations: RoleExplanations,
    private val roleSync: RoleSyncRequests,
    private val systemLog: SystemLogRecords,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
) {
    /** By a Discord ID, or part of the Discord or WarEra name, ignoring case; at most [MAX_SEARCH_RESULTS]. */
    fun search(query: String): List<MemberSummary> {
        val text = query.trim().lowercase()
        if (text.isEmpty()) return emptyList()
        val onServer = guildMembers.discordUserIds()
        val names = guildMembers.members().orEmpty().associate { it.discordUserId to it.name }
        val wareraNames = accountLinks.findAll().associate { it.discordUserId to it.wareraUsername }
        return (names.keys + wareraNames.keys + onServer.orEmpty())
            .asSequence()
            .map { id -> MemberSummary(id, names[id], wareraNames[id], onServer?.contains(id)) }
            .filter { member ->
                member.discordUserId == text ||
                    member.discordName?.lowercase()?.contains(text) == true ||
                    member.wareraUsername?.lowercase()?.contains(text) == true
            }.sortedBy { (it.discordName ?: it.wareraUsername ?: it.discordUserId).lowercase() }
            .take(MAX_SEARCH_RESULTS)
            .toList()
    }

    /** @throws ResponseStatusException 404 for a member who is neither on the guild nor linked */
    fun member(discordUserId: String): MemberDetailResponse {
        val link = accounts.storedData(discordUserId).link
        val onServer = guildMembers.discordUserIds()?.contains(discordUserId)
        if (link == null && onServer != true) throw notFound()
        val records = systemLog.recordsAbout(discordUserId)
        val events =
            records.entries.map {
                MemberLogEventResponse(
                    occurredAt = it.occurredAt.toEpochMilli(),
                    kind = it.kind,
                    syncTrigger = null,
                    role = null,
                    outcome = null,
                    reason = null,
                    wareraUsername = it.wareraUsername,
                    previousAccount = it.previousAccount,
                )
            } +
                records.items.map {
                    MemberLogEventResponse(
                        occurredAt = it.occurredAt.toEpochMilli(),
                        kind = it.entryKind,
                        syncTrigger = it.syncTrigger,
                        role = it.roleName ?: it.roleId,
                        outcome = it.outcome,
                        reason = it.reason,
                        wareraUsername = null,
                        previousAccount = false,
                    )
                }
        // The log keeps the name of a member who left, which the bot no longer reports.
        val loggedName = (records.items.mapNotNull { it.discordName } + records.entries.mapNotNull { it.discordName }).lastOrNull()
        return MemberDetailResponse(
            discordUserId = discordUserId,
            discordName = guildMember(discordUserId)?.name ?: loggedName,
            onServer = onServer,
            link = link?.let { MemberLinkResponse(it.wareraUserId, it.wareraUsername, it.verifiedAt.toEpochMilli()) },
            recentLog = events.sortedByDescending { it.occurredAt }.take(MAX_LOG_EVENTS),
        )
    }

    /**
     * Judged with fresh WarEra data like `/rollen`, with whether the member holds each role now.
     *
     * @throws eu.luftiger.bits.warera.WareraApiException if the member's WarEra data cannot be loaded
     */
    fun roles(discordUserId: String): MemberRolesResponse {
        val explanation = roleExplanations.explain(discordUserId) ?: return MemberRolesResponse(linked = false, roles = emptyList())
        val held = guildMember(discordUserId)?.roleIds
        return MemberRolesResponse(
            linked = true,
            roles =
                explanation.roles.map { role ->
                    MemberRoleResponse(
                        id = role.explanation.roleId,
                        name = role.roleName,
                        color = role.roleColor,
                        outcome =
                            when (role.explanation.outcome) {
                                ManagedRoleOutcome.MET -> MemberRoleOutcome.MET
                                ManagedRoleOutcome.NOT_MET -> MemberRoleOutcome.NOT_MET
                                ManagedRoleOutcome.UNDECIDED -> MemberRoleOutcome.UNDECIDED
                            },
                        held = held?.contains(role.explanation.roleId),
                        rules = role.explanation.rules.map { MemberRuleResponse(it.label, it.met, it.detail) },
                    )
                },
        )
    }

    /**
     * Syncs the member alone with fresh WarEra data; the bot reports the result to the system log like any sync.
     *
     * @throws ResponseStatusException 404 for an unknown member, 409 while the role sync, nicknames and status roles
     * are all switched off
     */
    fun sync(
        discordUserId: String,
        actor: AuditActor,
    ) {
        val linked = accountLinks.findByDiscordUserId(discordUserId) != null
        if (!linked && guildMembers.discordUserIds()?.contains(discordUserId) != true) throw notFound()
        val name = guildMember(discordUserId)?.name ?: discordUserId
        transactionTemplate.executeWithoutResult {
            auditLog.record(actor, AuditAction.MEMBER_SYNC_REQUESTED, "Abgleich von $name angestoßen")
            if (!roleSync.syncMember(discordUserId)) throw ResponseStatusException(HttpStatus.CONFLICT)
        }
    }

    /**
     * Works like `/unverify`: the link and an open code are deleted, and the following sync takes the managed roles.
     *
     * @throws ResponseStatusException 404 if the member has no link
     */
    fun unlink(
        discordUserId: String,
        actor: AuditActor,
    ) {
        transactionTemplate.executeWithoutResult {
            val link = accountLinks.findByDiscordUserId(discordUserId) ?: throw notFound()
            val name = guildMember(discordUserId)?.name ?: discordUserId
            accounts.unlink(discordUserId)
            auditLog.record(actor, AuditAction.MEMBER_UNLINKED, "Verknüpfung von $name mit ${link.wareraUsername} gelöst")
        }
    }

    private fun guildMember(discordUserId: String) = guildMembers.members()?.find { it.discordUserId == discordUserId }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    private companion object {
        const val MAX_SEARCH_RESULTS = 20

        /** Enough for the last few syncs; the system log page has the rest. */
        const val MAX_LOG_EVENTS = 20
    }
}
