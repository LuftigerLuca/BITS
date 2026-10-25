package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.shared.EmbassySyncEvent
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadStatus
import eu.luftiger.bits.shared.EmbassyThreadTarget
import eu.luftiger.bits.shared.ReportedEmbassyChange
import eu.luftiger.bits.shared.ReportedEmbassyThread
import eu.luftiger.bits.shared.ReportedThreadMember
import eu.luftiger.bits.shared.RoleChangeOutcome
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/**
 * Brings the embassy threads and the ambassador role to the target state from the backend, in this order: it reads
 * who is in each thread, gives the role to new ambassadors, since the role lets them see the channel, adds and removes
 * thread members, and only then takes the role from those who are no longer ambassadors.
 *
 * Members who may manage threads in the channel are never removed. A thread whose government could not be loaded loses
 * nobody, and its members keep the role. An archived thread is unarchived only if something changes. A thread that is
 * gone or that the bot may not manage, and every change Discord refuses, is reported and skipped, so the rest goes on.
 * What it did goes to [onApplied] for the backend.
 */
class EmbassySyncHandler(
    private val guild: EmbassyGuild,
    private val onApplied: (syncId: Long, report: EmbassySyncReport) -> Unit = { _, _ -> },
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun handle(event: EmbassySyncEvent) {
        val members = guild.members()
        val hostReasons = hostReasons(event, members)
        val changes = mutableListOf<ReportedEmbassyChange>()
        val reports = mutableListOf<ReportedEmbassyThread>()
        val loaded = event.threads.mapNotNull { load(it, reports) }
        val role = event.ambassadorRoleId?.let { RoleTarget(it, guild.role(it)) }
        role?.let { changes += grantRole(it, event) }
        loaded.forEach { reports += sync(it, hostReasons, changes) }
        // Members of a thread that keeps everybody keep the role too, since BITS cannot tell whether they hold an office.
        val kept = loaded.filterNot { it.target.complete }.flatMapTo(HashSet()) { it.memberIds }
        role?.let { changes += removeRole(it, event, members, kept) }
        onApplied(event.syncId, EmbassySyncReport(reports.sortedBy { it.threadId }, changes))
        log.info(
            "Embassy sync applied to {} of {} threads, {} changes",
            reports.count { it.status == EmbassyThreadStatus.SYNCED },
            event.threads.size,
            changes.count { it.outcome == RoleChangeOutcome.GRANTED || it.outcome == RoleChangeOutcome.REMOVED },
        )
    }

    private class LoadedThread(
        val target: EmbassyThreadTarget,
        val thread: EmbassyThreadChannel,
        /** Without the bot itself. */
        val memberIds: Set<String>,
    )

    private class RoleTarget(
        val id: String,
        /** `null` if the role is gone. */
        val role: EmbassyRole?,
    )

    /** `null` for a thread that cannot be synced; its report goes to [reports] then. */
    private suspend fun load(
        target: EmbassyThreadTarget,
        reports: MutableList<ReportedEmbassyThread>,
    ): LoadedThread? {
        fun unsynced(
            name: String?,
            status: EmbassyThreadStatus,
        ): LoadedThread? {
            reports += unsynced(target, name, status)
            return null
        }
        val thread =
            when (val lookup = attempt { guild.thread(target.threadId, target.parentChannelId) }) {
                is ThreadLookup.Found -> lookup.thread
                ThreadLookup.Missing -> return unsynced(null, EmbassyThreadStatus.MISSING)
                ThreadLookup.NoPermission -> return unsynced(null, EmbassyThreadStatus.NO_PERMISSION)
                null -> return unsynced(null, EmbassyThreadStatus.FAILED)
            }
        if (!thread.manageable) return unsynced(thread.name, EmbassyThreadStatus.NO_PERMISSION)
        val memberIds = attempt { thread.memberIds() } ?: return unsynced(thread.name, EmbassyThreadStatus.FAILED)
        return LoadedThread(target, thread, memberIds - guild.selfId)
    }

    private fun unsynced(
        target: EmbassyThreadTarget,
        name: String?,
        status: EmbassyThreadStatus,
    ): ReportedEmbassyThread {
        log.warn("Embassy thread {} not synced: {}", target.threadId, status)
        return ReportedEmbassyThread(target.threadId, name, status, emptyList())
    }

    /** By Discord user ID, the host role each member of a host role belongs in the threads for. */
    private fun hostReasons(
        event: EmbassySyncEvent,
        members: List<EmbassyMember>,
    ): Map<String, String> {
        val names = event.hostRoleIds.associateWith { guild.role(it)?.name }
        return members
            .mapNotNull { member ->
                event.hostRoleIds.firstOrNull { it in member.roleIds }?.let { roleId ->
                    member.id to "Gastgeber-Rolle @${names[roleId] ?: roleId}"
                }
            }.toMap()
    }

    private suspend fun sync(
        loaded: LoadedThread,
        hostReasons: Map<String, String>,
        changes: MutableList<ReportedEmbassyChange>,
    ): ReportedEmbassyThread {
        val target = loaded.target
        val thread = loaded.thread
        val current = loaded.memberIds
        // Members who left the guild cannot be added.
        val wanted = (target.members + hostReasons.filterKeys { it !in target.members }).filterKeys { guild.member(it) != null }
        val additions = wanted.keys - current
        val removals =
            if (target.complete) current.filter { it !in wanted && it != guild.selfId && !thread.mayManageThreads(it) } else emptyList()
        if (additions.isEmpty() && removals.isEmpty()) return synced(target, thread, current, wanted)
        if (thread.archived && attempt { thread.unarchive(UNARCHIVE_REASON) } == null) {
            return unsynced(target, thread.name, EmbassyThreadStatus.FAILED)
        }
        val members = current.toMutableSet()
        additions.forEach { id ->
            val refusal = add(thread, id)
            if (refusal == null) members += id
            changes += threadChange(id, target, thread, refusal == null, add = true, reason = refusal ?: wanted.getValue(id))
        }
        removals.forEach { id ->
            val ok = attempt { thread.remove(id) } != null
            if (ok) members -= id
            changes += threadChange(id, target, thread, ok, add = false, reason = if (ok) "Weder Amt noch manuell hinzugefügt" else REFUSED)
        }
        return synced(target, thread, members, wanted)
    }

    private fun synced(
        target: EmbassyThreadTarget,
        thread: EmbassyThreadChannel,
        memberIds: Set<String>,
        wanted: Map<String, String>,
    ) = ReportedEmbassyThread(
        threadId = target.threadId,
        threadName = thread.name,
        status = EmbassyThreadStatus.SYNCED,
        members =
            memberIds.map { id ->
                val reason =
                    wanted[id]
                        ?: when {
                            thread.mayManageThreads(id) -> "Darf Threads verwalten"
                            !target.complete -> "Behalten: Regierung nicht ladbar"
                            else -> "Konnte nicht entfernt werden"
                        }
                ReportedThreadMember(id, name(id), reason)
            },
    )

    private fun threadChange(
        discordUserId: String,
        target: EmbassyThreadTarget,
        thread: EmbassyThreadChannel,
        ok: Boolean,
        add: Boolean,
        /** Why it was done, or why Discord refused. */
        reason: String,
    ) = ReportedEmbassyChange(
        discordUserId = discordUserId,
        memberName = name(discordUserId),
        threadId = target.threadId,
        threadName = thread.name,
        outcome =
            when {
                !ok -> RoleChangeOutcome.FAILED
                add -> RoleChangeOutcome.GRANTED
                else -> RoleChangeOutcome.REMOVED
            },
        reason = reason,
    )

    /** `null` if [discordUserId] was added, otherwise why not, in German. */
    private suspend fun add(
        thread: EmbassyThreadChannel,
        discordUserId: String,
    ): String? =
        try {
            thread.add(discordUserId)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: MemberCannotViewException) {
            log.warn("Member {} may not see #{}, so Discord refused to add them to an embassy thread", discordUserId, e.channelName)
            "Sieht #${e.channelName} nicht: Die Botschafter-Rolle braucht dort „Kanal ansehen“."
        } catch (e: Exception) {
            log.error("Discord refused a change of the embassy sync", e)
            REFUSED
        }

    private suspend fun grantRole(
        target: RoleTarget,
        event: EmbassySyncEvent,
    ): List<ReportedEmbassyChange> =
        event.ambassadors.mapNotNull { (id, reason) ->
            val member = guild.member(id) ?: return@mapNotNull null
            if (target.id in member.roleIds) return@mapNotNull null
            roleChange(target, member, reason, add = true)
        }

    private suspend fun removeRole(
        target: RoleTarget,
        event: EmbassySyncEvent,
        members: List<EmbassyMember>,
        kept: Set<String>,
    ): List<ReportedEmbassyChange> =
        members
            .filter { target.id in it.roleIds && it.id !in event.ambassadors && it.id !in kept }
            .map { roleChange(target, it, REMOVE_ROLE_REASON, add = false) }

    private suspend fun roleChange(
        target: RoleTarget,
        member: EmbassyMember,
        reason: String,
        add: Boolean,
    ): ReportedEmbassyChange {
        val change =
            ReportedEmbassyChange(
                member.id,
                member.name,
                roleId = target.id,
                roleName = target.role?.name,
                outcome = RoleChangeOutcome.SKIPPED,
            )
        if (target.role?.assignable != true) return change.copy(reason = NOT_ASSIGNABLE)
        val ok =
            attempt { if (add) guild.grantRole(member.id, target.id, reason) else guild.removeRole(member.id, target.id, reason) } != null
        return when {
            !ok -> change.copy(outcome = RoleChangeOutcome.FAILED, reason = REFUSED)
            add -> change.copy(outcome = RoleChangeOutcome.GRANTED, reason = reason.removePrefix("BITS: "))
            else -> change.copy(outcome = RoleChangeOutcome.REMOVED, reason = reason.removePrefix("BITS: "))
        }
    }

    private fun name(discordUserId: String) = guild.member(discordUserId)?.name ?: discordUserId

    /** `null` if Discord refused; the failure is logged and the sync goes on. */
    private suspend fun <T : Any> attempt(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Discord refused a change of the embassy sync", e)
            null
        }

    private companion object {
        const val REFUSED = "Discord hat die Änderung abgelehnt"
        const val REMOVE_ROLE_REASON = "BITS: in keinem Botschafts-Thread mehr"
        const val UNARCHIVE_REASON = "BITS: Abgleich des Botschafts-Threads"
        const val NOT_ASSIGNABLE = "Die Botschafter-Rolle fehlt in Discord oder steht über der Rolle des Bots."
    }
}
