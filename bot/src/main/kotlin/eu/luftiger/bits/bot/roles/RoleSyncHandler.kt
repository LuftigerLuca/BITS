package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.NicknameChangeOutcome
import eu.luftiger.bits.shared.ReportedNicknameChange
import eu.luftiger.bits.shared.ReportedRoleChange
import eu.luftiger.bits.shared.RoleChangeOutcome
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.RoleSyncReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Brings the managed roles of the members to the target state from the backend. A change that fails is logged and
 * skipped instead of failing the event, since the next sync tries again anyway. Members whose roles a newer sync has
 * set already are left alone, and a role that a sync would take from too many of its holders stays, see
 * [RemovalLimit]. All changes of one member go to Discord in one request, see [applyRoleChanges]. The status roles go
 * to every member but bots and are never held back, see [statusRoleChanges]. If the event carries nicknames, it sets
 * them too, see [nicknameChange]. What it changed, skipped and failed goes to [onApplied] for the backend's system log,
 * with the names of members and roles as they are now.
 */
class RoleSyncHandler(
    private val jda: JDA,
    private val guildId: String,
    private val removalLimit: RemovalLimit = RemovalLimit(),
    private val onApplied: (syncId: Long, report: RoleSyncReport) -> Unit = { _, _ -> },
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val appliedSyncs = AppliedSyncs()

    suspend fun handle(event: RoleSyncEvent) {
        val guild = jda.getGuildById(guildId) ?: error("Bot is not a member of guild $guildId")
        val (stale, members) =
            (event.discordUserIds?.mapNotNull { findMember(it) } ?: guild.members)
                .partition { appliedSyncs.isStale(it.id, event) }
        val membersById = members.associateBy { it.id }
        val proposed = members.flatMap { member -> roleChanges(member.id, member.roles.mapTo(HashSet()) { it.id }, event) }
        val holders = members.flatMap { member -> member.roles.map { it.id } }.groupingBy { it }.eachCount()
        val heldBack = rolesOverRemovalLimit(proposed, holders, event, removalLimit)
        heldBack.forEach { (roleId, removals) ->
            log.warn(
                "Role sync would take role {} from {} of its {} holders, more than the limit, so nobody loses it. Missing " +
                    "WarEra data is the likely cause; if the change is real, any change to a rule of the role applies it.",
                roleId,
                removals,
                holders[roleId],
            )
        }
        val statusChanges =
            if (event.statusRoles == null) {
                emptyList()
            } else {
                members
                    .filterNot { it.user.isBot }
                    .flatMap { member -> statusRoleChanges(member.id, member.roles.mapTo(HashSet()) { it.id }, event) }
            }
        val changes = proposed.filterNot { !it.add && it.roleId in heldBack } + statusChanges
        val outcomes = mutableListOf<Pair<RoleChange, Outcome>>()
        proposed.filter { !it.add && it.roleId in heldBack }.forEach { outcomes += it to Outcome(RoleChangeOutcome.SKIPPED, HELD_BACK) }
        val results = applyRoleChanges(guild, membersById, changes)
        changes.forEach { outcomes += it to results.getValue(it) }
        val applied = results.values.count { it.outcome in APPLIED_ROLES }
        val nicknameChanges = applyNicknames(guild, members, event)
        appliedSyncs.record(event)
        event.syncId?.let { syncId ->
            val report =
                RoleSyncReport(
                    memberCount = members.size,
                    staleMemberCount = stale.size,
                    changes =
                        outcomes.map { (change, outcome) ->
                            ReportedRoleChange(
                                discordUserId = change.discordUserId,
                                memberName = membersById.getValue(change.discordUserId).effectiveName,
                                roleId = change.roleId,
                                roleName = guild.getRoleById(change.roleId)?.name,
                                outcome = outcome.outcome,
                                reason = outcome.reason,
                            )
                        },
                    nicknameChanges = nicknameChanges,
                )
            onApplied(syncId, report)
        }
        log.info(
            "Role sync applied {} of {} changes for {} members, skipped {} with a newer sync",
            applied,
            changes.size,
            members.size,
            stale.size,
        )
    }

    /**
     * Applies the changes of each member with one request, since Discord limits how many role changes a guild may
     * make: a sync that gives one member a role and takes another costs one request instead of two. A failing request
     * fails all changes of its member.
     */
    private suspend fun applyRoleChanges(
        guild: Guild,
        membersById: Map<String, Member>,
        changes: List<RoleChange>,
    ): Map<RoleChange, Outcome> {
        val results = HashMap<RoleChange, Outcome>()
        val started = TimeSource.Monotonic.markNow()
        val (assignable, unassignable) =
            changes.partition { change -> guild.getRoleById(change.roleId)?.let { guild.selfMember.canInteract(it) } == true }
        unassignable.forEach { change ->
            log.warn("Skipping role {} for member {}: it is gone or above the bot", change.roleId, change.discordUserId)
            results[change] = Outcome(RoleChangeOutcome.SKIPPED, NOT_ASSIGNABLE)
        }
        assignable.groupBy { it.discordUserId }.forEach { (discordUserId, memberChanges) ->
            val (adds, removes) = memberChanges.partition { it.add }
            try {
                guild
                    .modifyMemberRoles(
                        membersById.getValue(discordUserId),
                        adds.map { guild.getRoleById(it.roleId)!! },
                        removes.map { guild.getRoleById(it.roleId)!! },
                    ).reason(
                        memberChanges
                            .map { it.reason }
                            .distinct()
                            .joinToString("; ")
                            .take(MAX_REASON_LENGTH),
                    ).submit()
                    .await()
                memberChanges.forEach {
                    results[it] = Outcome(if (it.add) RoleChangeOutcome.GRANTED else RoleChangeOutcome.REMOVED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Changing the roles of member {} failed", discordUserId, e)
                // Short, so that the backend takes the report; it allows 1000 characters.
                val outcome = Outcome(RoleChangeOutcome.FAILED, "Discord hat die Änderung abgelehnt: ${e.message?.take(500)}")
                memberChanges.forEach { results[it] = outcome }
            }
        }
        if (assignable.isNotEmpty()) {
            log.info(
                "Changed roles of {} members in {}",
                assignable.distinctBy { it.discordUserId }.size,
                started.elapsedNow().inWholeSeconds.seconds,
            )
        }
        return results
    }

    /** The owner and members above the bot cannot be renamed by any bot; that is skipped, not failed. */
    private suspend fun applyNicknames(
        guild: Guild,
        members: List<Member>,
        event: RoleSyncEvent,
    ): List<ReportedNicknameChange> {
        val changes = members.mapNotNull { member -> nicknameChange(member.id, member.nickname, event)?.let { member to it } }
        if (changes.isEmpty()) return emptyList()
        val mayManage = guild.selfMember.hasPermission(Permission.NICKNAME_MANAGE)
        val reports =
            changes.map { (member, change) ->
                val (outcome, reason) =
                    when {
                        !mayManage -> NicknameChangeOutcome.SKIPPED to NO_NICKNAME_PERMISSION
                        !guild.selfMember.canInteract(member) -> NicknameChangeOutcome.SKIPPED to MEMBER_ABOVE_BOT
                        else -> changeNickname(guild, member, change)
                    }
                ReportedNicknameChange(member.id, member.effectiveName, change.nickname, outcome, reason)
            }
        if (!mayManage) log.warn("Skipping {} nickname changes: the bot may not manage nicknames", changes.size)
        log.info("Nickname sync changed {} of {} nicknames", reports.count { it.outcome in APPLIED_NICKNAMES }, changes.size)
        return reports
    }

    private suspend fun changeNickname(
        guild: Guild,
        member: Member,
        change: NicknameChange,
    ): Pair<NicknameChangeOutcome, String?> =
        try {
            guild
                .modifyNickname(member, change.nickname)
                .reason(NICKNAME_REASON)
                .submit()
                .await()
            (if (change.nickname == null) NicknameChangeOutcome.RESET else NicknameChangeOutcome.SET) to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Changing the nickname of member {} failed", member.id, e)
            NicknameChangeOutcome.FAILED to "Discord hat die Änderung abgelehnt: ${e.message?.take(500)}"
        }

    private class Outcome(
        val outcome: RoleChangeOutcome,
        val reason: String? = null,
    )

    /** Members who left the guild are skipped. */
    private suspend fun findMember(discordUserId: String): Member? {
        val guild = jda.getGuildById(guildId) ?: return null
        guild.getMemberById(discordUserId)?.let { return it }
        return try {
            guild.retrieveMemberById(discordUserId).submit().await()
        } catch (e: ErrorResponseException) {
            if (e.errorResponse == ErrorResponse.UNKNOWN_MEMBER || e.errorResponse == ErrorResponse.UNKNOWN_USER) null else throw e
        }
    }

    private companion object {
        const val HELD_BACK = "Zurückgehalten: Der Sync hätte die Rolle zu vielen Mitgliedern entzogen."
        const val NOT_ASSIGNABLE = "Die Rolle fehlt in Discord oder steht über der Rolle des Bots."
        const val NO_NICKNAME_PERMISSION = "Dem Bot fehlt die Berechtigung „Nicknames verwalten“."
        val APPLIED_ROLES = setOf(RoleChangeOutcome.GRANTED, RoleChangeOutcome.REMOVED)
        val APPLIED_NICKNAMES = setOf(NicknameChangeOutcome.SET, NicknameChangeOutcome.RESET)

        /** Discord's limit for the reason in the audit log. */
        const val MAX_REASON_LENGTH = 512
        const val MEMBER_ABOVE_BOT = "Das Mitglied besitzt den Server oder steht über der Rolle des Bots."
    }
}
