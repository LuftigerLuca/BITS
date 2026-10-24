package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.RoleSyncEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory

/**
 * Brings the managed roles of the members to the target state from the backend. A change that fails is logged and
 * skipped instead of failing the event, since the next sync tries again anyway. Members whose roles a newer sync has
 * set already are left alone, and a role that a sync would take from too many of its holders stays, see
 * [RemovalLimit].
 */
class RoleSyncHandler(
    private val jda: JDA,
    private val guildId: String,
    private val removalLimit: RemovalLimit = RemovalLimit(),
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
        val changes = proposed.filterNot { !it.add && it.roleId in heldBack }
        var applied = 0
        changes.forEach { change ->
            val role = guild.getRoleById(change.roleId)
            if (role == null || !guild.selfMember.canInteract(role)) {
                log.warn("Skipping role {} for member {}: it is gone or above the bot", change.roleId, change.discordUserId)
                return@forEach
            }
            try {
                val member = membersById.getValue(change.discordUserId)
                val action = if (change.add) guild.addRoleToMember(member, role) else guild.removeRoleFromMember(member, role)
                action.reason(change.reason).submit().await()
                applied++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Changing role {} of member {} failed", change.roleId, change.discordUserId, e)
            }
        }
        appliedSyncs.record(event)
        log.info(
            "Role sync applied {} of {} changes for {} members, skipped {} with a newer sync",
            applied,
            changes.size,
            members.size,
            stale.size,
        )
    }

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
}
