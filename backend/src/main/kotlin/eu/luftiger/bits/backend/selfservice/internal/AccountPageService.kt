package eu.luftiger.bits.backend.selfservice.internal

import eu.luftiger.bits.backend.account.api.AccountSelfService
import eu.luftiger.bits.backend.audit.api.AuditEntries
import eu.luftiger.bits.backend.ping.api.PingLogs
import eu.luftiger.bits.backend.rolesync.api.RoleExplanations
import eu.luftiger.bits.backend.systemlog.api.SystemLogRecords
import eu.luftiger.bits.shared.ManagedRoleOutcome
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.time.Clock

/** Gathers what the modules keep about one member; each module decides itself what belongs to the member. */
@Service
class AccountPageService(
    private val accounts: AccountSelfService,
    private val roleExplanations: RoleExplanations,
    private val systemLog: SystemLogRecords,
    private val pings: PingLogs,
    private val audit: AuditEntries,
    private val clock: Clock,
) {
    fun account(discordUserId: String): AccountResponse =
        AccountResponse(
            link = accounts.storedData(discordUserId).link?.let { LinkResponse(it.wareraUsername, it.verifiedAt.toEpochMilli()) },
            lastSyncAt = systemLog.lastSyncOfAll()?.toEpochMilli(),
        )

    /** @throws eu.luftiger.bits.warera.WareraApiException if the member's WarEra data cannot be loaded */
    fun roles(discordUserId: String): AccountRolesResponse {
        val explanation = roleExplanations.explain(discordUserId) ?: return AccountRolesResponse(linked = false, roles = emptyList())
        return AccountRolesResponse(
            linked = true,
            roles =
                explanation.roles.map { role ->
                    AccountRoleResponse(
                        id = role.explanation.roleId,
                        name = role.roleName,
                        color = role.roleColor,
                        outcome =
                            when (role.explanation.outcome) {
                                ManagedRoleOutcome.MET -> RoleOutcomeResponse.MET
                                ManagedRoleOutcome.NOT_MET -> RoleOutcomeResponse.NOT_MET
                                ManagedRoleOutcome.UNDECIDED -> RoleOutcomeResponse.UNDECIDED
                            },
                        rules = role.explanation.rules.map { AccountRuleResponse(it.label, it.met, it.detail) },
                    )
                },
        )
    }

    fun unlink(discordUserId: String): Boolean = accounts.unlink(discordUserId)

    /**
     * The audit log is searched by the member's own session subject, so it holds only what the member did as admin;
     * changes of other admins are not about the member.
     */
    fun export(discordUserId: String): String = EXPORT_JSON.encodeToString(DataExport.serializer(), dataExport(discordUserId))

    private fun dataExport(discordUserId: String): DataExport {
        val stored = accounts.storedData(discordUserId)
        val log = systemLog.recordsAbout(discordUserId)
        return DataExport(
            discordUserId = discordUserId,
            exportedAt = clock.instant().toString(),
            link = stored.link?.let { ExportedLink(it.wareraUserId, it.wareraUsername, it.verifiedAt.toString()) },
            openVerification =
                stored.verification?.let {
                    ExportedVerification(it.wareraUserId, it.wareraUsername, it.code, it.expiresAt.toString())
                },
            nicknameOptedInAt = stored.nicknameOptedInAt?.toString(),
            pings = pings.pingsOf(discordUserId).map { it.toString() },
            systemLog =
                ExportedSystemLog(
                    entries =
                        log.entries.map {
                            ExportedLogEntry(
                                it.occurredAt.toString(),
                                it.kind,
                                it.discordName,
                                it.wareraUserId,
                                it.wareraUsername,
                                it.previousAccount,
                            )
                        },
                    items =
                        log.items.map {
                            ExportedLogItem(
                                it.occurredAt.toString(),
                                it.entryKind,
                                it.syncTrigger,
                                it.discordName,
                                it.roleId,
                                it.roleName,
                                it.outcome,
                                it.reason,
                            )
                        },
                ),
            auditLog =
                audit.byActor(DISCORD_SUBJECT_PREFIX + discordUserId).map {
                    ExportedAuditEntry(it.occurredAt.toString(), it.actorName, it.actorAvatarUrl, it.action.name, it.description)
                },
        )
    }

    private companion object {
        /** The session subject of Discord users, see `SessionUser` in the auth module. */
        const val DISCORD_SUBJECT_PREFIX = "discord:"

        /** Read by people rather than programs, so indented and with every field, `null` ones too. */
        val EXPORT_JSON =
            Json {
                prettyPrint = true
                encodeDefaults = true
                explicitNulls = true
            }
    }
}
