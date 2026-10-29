package eu.luftiger.bits.backend.members.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.account.api.AccountRemoval
import eu.luftiger.bits.backend.account.api.dto.DepartedMember
import eu.luftiger.bits.backend.members.api.GuildMembers
import eu.luftiger.bits.backend.members.api.MemberJoined
import eu.luftiger.bits.backend.members.api.dto.GuildMember
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Keeps the members of the guild in memory and deletes the links of those who left. A full list from the bot also
 * catches members who left while it was offline, and links from before BITS deleted them on leaving.
 *
 * A list that would delete more than half of all links, and at least [MIN_REFUSED_REMOVALS], looks more like a bug
 * than like members leaving, so it is refused as a whole: nothing is deleted and the previous list stays. The refusal
 * is recorded in the [SystemLog] once, not on every report that follows.
 *
 * A member who joins is announced as [MemberJoined], e.g. for the role of unverified members.
 */
@Service
class GuildMemberService(
    private val accountLinks: AccountLinks,
    private val accountRemoval: AccountRemoval,
    private val systemLog: SystemLog,
    private val applicationEvents: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) : GuildMembers {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var members: Set<String>? = null

    @Volatile
    private var roles: Map<String, GuildMember>? = null
    private var refusing = false

    override fun discordUserIds(): Set<String>? = members

    override fun members(): Collection<GuildMember>? = roles?.values

    /** [withRoles] is empty from a bot that does not report roles yet. */
    @Synchronized
    fun replaceAll(
        discordUserIds: Set<String>,
        withRoles: List<GuildMember> = emptyList(),
    ) {
        val linked = accountLinks.findAll().map { it.discordUserId }
        val departed = linked.filter { it !in discordUserIds }
        if (departed.size >= MIN_REFUSED_REMOVALS && departed.size * 2 > linked.size) {
            log.warn("Member list refused, it would delete {} of {} links", departed.size, linked.size)
            if (!refusing) systemLog.recordProblem(SystemProblem.MemberReportRefused(departed.size, linked.size))
            refusing = true
            return
        }
        refusing = false
        if (departed.isNotEmpty()) accountRemoval.removeDeparted(departed.map { DepartedMember(it) })
        members = discordUserIds
        roles = if (withRoles.isEmpty() && discordUserIds.isNotEmpty()) null else withRoles.associateBy { it.discordUserId }
        log.info("Member list of {} members received, {} links of departed members deleted", discordUserIds.size, departed.size)
    }

    @Synchronized
    fun joined(discordUserId: String) {
        members = members?.plus(discordUserId)
        roles = roles?.plus(discordUserId to GuildMember(discordUserId, name = null, roleIds = emptySet()))
        // In a transaction, so the event publication registry keeps it until the listeners are done.
        transactionTemplate.executeWithoutResult { applicationEvents.publishEvent(MemberJoined(discordUserId)) }
    }

    /** Deletes the link right away, without the check of [replaceAll]: a single departure is no sign of a broken list. */
    @Synchronized
    fun left(
        discordUserId: String,
        discordName: String?,
    ) {
        members = members?.minus(discordUserId)
        roles = roles?.minus(discordUserId)
        accountRemoval.removeDeparted(listOf(DepartedMember(discordUserId, discordName)))
    }

    /** Back to the state after a start of the backend, for tests. */
    @Synchronized
    internal fun forget() {
        members = null
        roles = null
        refusing = false
    }

    private companion object {
        /** Like the bot's removal limit of the role sync, so that a guild with few links is never stuck. */
        const val MIN_REFUSED_REMOVALS = 10
    }
}
