package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.vote.domain.Choice
import eu.luftiger.bits.backend.vote.domain.Elector
import eu.luftiger.bits.backend.vote.domain.Vote
import eu.luftiger.bits.backend.vote.domain.VoteManagerRole
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import eu.luftiger.bits.shared.BitsEvent
import eu.luftiger.bits.shared.VoteClosedEvent
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import eu.luftiger.bits.shared.VoteStartedEvent
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Starts, runs and ends votes, from Discord and from the dashboard alike. Who may manage them is decided here, with
 * the roles the bot or the session token reports, see [VoteManager].
 *
 * Ballots are kept per member until the vote is evaluated or cancelled, so a member can change their vote. Casting
 * holds a shared lock on the vote and closing an exclusive one, so no ballot slips in after the end. The result is
 * worked out in the background by [VoteEvaluator] once the bot has reported the members of the role.
 */
@Service
class VoteService(
    private val voteRepository: VoteRepository,
    private val ballotRepository: BallotRepository,
    private val electorRepository: ElectorRepository,
    private val partyResultRepository: PartyResultRepository,
    private val nonVoterRepository: NonVoterRepository,
    private val managerRoleRepository: VoteManagerRoleRepository,
    private val channelRepository: DiscordChannelRepository,
    private val guildRoles: GuildRoles,
    private val accountLinks: AccountLinks,
    private val eventPublisher: EventPublisher,
    private val auditLog: AuditLog,
    private val evaluator: VoteEvaluator,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    fun mayManage(manager: VoteManager): Boolean {
        if (manager.admin) return true
        val managerRoles = managerRoleRepository.findAll().mapTo(HashSet()) { it.roleId }
        return manager.roleIds.any { it in managerRoles }
    }

    fun managerRoleIds(): List<String> = managerRoleRepository.findAll().map { it.roleId }.sorted()

    /** Only admins get here, see the controller. */
    fun setManagerRoleIds(
        roleIds: List<String>,
        actor: AuditActor,
    ) {
        val known = guildRoles.all().associateBy { it.id }
        val unknown = roleIds.filter { it !in known }
        if (unknown.isNotEmpty()) throw badRequest("Unknown roles $unknown")
        transactionTemplate.executeWithoutResult {
            managerRoleRepository.deleteAllInBatch()
            managerRoleRepository.saveAll(roleIds.distinct().map(::VoteManagerRole))
            val names = roleIds.distinct().mapNotNull { known[it]?.name }.sorted()
            auditLog.record(
                actor,
                AuditAction.VOTE_MANAGER_ROLES_CHANGED,
                if (names.isEmpty()) "Abstimmungen verwalten: nur Admins" else "Abstimmungen verwalten: ${names.joinToString { "@$it" }}",
            )
        }
    }

    /** @throws ResponseStatusException 400 if the question is empty or too long, the end has passed or the role is unknown */
    fun start(
        manager: VoteManager,
        question: String,
        roleId: String,
        channelId: String,
        endsAt: Instant?,
    ): StartOutcome {
        if (!mayManage(manager)) return StartOutcome.Forbidden
        val text = question.trim()
        if (text.isEmpty() || text.length > MAX_QUESTION_LENGTH) throw badRequest("Question must have 1 to $MAX_QUESTION_LENGTH characters")
        if (endsAt != null && !endsAt.isAfter(clock.instant())) throw badRequest("The end has passed already")
        val role = guildRoles.all().firstOrNull { it.id == roleId } ?: throw badRequest("Unknown role $roleId")
        val channel = channelRepository.findById(channelId).orElse(null)
        if (channel == null || !channel.postable) return StartOutcome.ChannelNotPostable
        val vote =
            requireNotNull(
                transactionTemplate.execute {
                    val saved =
                        voteRepository.save(
                            Vote(text, role.id, role.name, channel.id, channel.name, clock.instant(), manager.name, endsAt),
                        )
                    val end = endsAt?.let { "bis ${format(it)}" } ?: "ohne Enddatum"
                    auditLog.record(manager.auditActor(), AuditAction.VOTE_STARTED, "${describe(saved)}, $end")
                    eventPublisher.publish(VoteStartedEvent(requireNotNull(saved.id), clock.millis()))
                    saved
                },
            )
        return StartOutcome.Started(vote)
    }

    /**
     * Ends the vote, with a result unless [cancel]. A cancelled vote loses its ballots right away. [manager] is `null`
     * when the vote ends on its own.
     */
    fun close(
        voteId: Long,
        manager: VoteManager?,
        cancel: Boolean,
    ): CloseOutcome {
        if (manager != null && !mayManage(manager)) return CloseOutcome.Forbidden
        return requireNotNull(
            transactionTemplate.execute {
                val vote = voteRepository.findForUpdate(voteId)
                if (vote == null || vote.status != VoteStatus.OPEN) return@execute CloseOutcome.NotOpen
                val now = clock.instant()
                vote.closedAt = now
                vote.closedByName = manager?.name
                vote.lastAttemptAt = now
                if (cancel) {
                    vote.status = VoteStatus.CANCELLED
                    ballotRepository.deleteByVoteId(voteId)
                } else {
                    vote.status = VoteStatus.COUNTING
                }
                manager?.let {
                    val action = if (cancel) AuditAction.VOTE_CANCELLED else AuditAction.VOTE_ENDED
                    auditLog.record(it.auditActor(), action, describe(vote))
                }
                eventPublisher.publish(VoteClosedEvent(voteId, clock.millis()))
                CloseOutcome.Closed
            },
        )
    }

    /** Ends every vote whose end has come; called every minute. */
    fun closeDue() {
        voteRepository.findDueIds(clock.instant()).forEach { close(it, manager = null, cancel = false) }
    }

    /** The bot has checked that the member has the role; whether they are linked is checked here. */
    fun cast(
        voteId: Long,
        discordUserId: String,
        choice: Choice,
    ): CastOutcome =
        requireNotNull(
            transactionTemplate.execute {
                val vote = voteRepository.findForBallot(voteId)
                if (vote == null || !vote.acceptsBallots()) return@execute CastOutcome.Closed
                val link = accountLinks.findByDiscordUserId(discordUserId) ?: return@execute CastOutcome.NotLinked
                val previous = ballotRepository.cast(voteId, discordUserId, link.wareraUserId, choice.name, clock.instant())
                CastOutcome.Cast(previous?.let(Choice::valueOf))
            },
        )

    /**
     * Counts the votes of [roleMemberIds], the members of the role right now. Members without a link cannot vote and
     * do not count, unless they voted before they lost it.
     *
     * @throws ResponseStatusException 404 if there is no such vote
     */
    fun participation(
        voteId: Long,
        roleMemberIds: Collection<String>,
    ): Participation =
        requireNotNull(
            transactionTemplate.execute {
                val vote = voteRepository.findById(voteId).orElseThrow { notFound() }
                val voters = ballotRepository.findByVoteId(voteId).mapTo(HashSet()) { it.discordUserId }
                val linked = accountLinks.findAll().mapTo(HashSet()) { it.discordUserId }
                val members = roleMemberIds.toSet()
                val participation =
                    Participation(
                        voted = members.count { it in voters },
                        eligible = members.count { it in linked || it in voters },
                    )
                if (vote.status == VoteStatus.OPEN) vote.eligibleCount = participation.eligible
                participation
            },
        )

    /** @throws ResponseStatusException 404 if there is no such vote */
    fun find(voteId: Long): Vote = voteRepository.findById(voteId).orElseThrow { notFound() }

    fun open(): List<Vote> = voteRepository.findByStatusOrderByStartedAtDesc(VoteStatus.OPEN)

    /** Ended votes, the newest first. */
    fun closed(): List<Vote> =
        voteRepository.findByStatusInOrderByClosedAtDesc(listOf(VoteStatus.COUNTING, VoteStatus.EVALUATED, VoteStatus.CANCELLED))

    fun votedCount(voteId: Long): Int = ballotRepository.countByVoteId(voteId).toInt()

    /** The results of the evaluated ones among [votes], by vote ID. */
    fun results(votes: List<Vote>): Map<Long, VoteResult> {
        val evaluated = votes.filter { it.status == VoteStatus.EVALUATED }.associateBy { requireNotNull(it.id) }
        if (evaluated.isEmpty()) return emptyMap()
        val parties = partyResultRepository.findByVoteIdInOrderByPosition(evaluated.keys).groupBy { it.voteId }
        val nonVoters = nonVoterRepository.findByVoteIdInOrderByPosition(evaluated.keys).groupBy { it.voteId }
        return evaluated.mapValues { (id, vote) ->
            VoteResult(vote.question, parties[id].orEmpty(), nonVoters[id].orEmpty().map { it.name })
        }
    }

    /** @throws ResponseStatusException 404 if there is no evaluated vote of this ID */
    fun result(voteId: Long): Pair<Vote, VoteResult> {
        val vote = find(voteId)
        return vote to (results(listOf(vote))[voteId] ?: throw notFound())
    }

    /** @throws ResponseStatusException 404 if there is no such vote */
    fun reportMessage(
        voteId: Long,
        messageId: String,
    ) {
        transactionTemplate.executeWithoutResult {
            voteRepository.findById(voteId).orElseThrow { notFound() }.messageId = messageId
        }
    }

    /**
     * Keeps the members of the role at the end and starts the evaluation. A second report, e.g. after the backend
     * asked again, changes nothing.
     *
     * @throws ResponseStatusException 404 if there is no such vote
     */
    fun reportElectorate(
        voteId: Long,
        electors: List<Elector>,
    ) {
        val accepted =
            requireNotNull(
                transactionTemplate.execute {
                    val vote = voteRepository.findForUpdate(voteId) ?: throw notFound()
                    if (vote.status != VoteStatus.COUNTING || vote.electorateReportedAt != null) return@execute false
                    electorRepository.saveAll(electors.distinctBy { it.discordUserId })
                    vote.electorateReportedAt = clock.instant()
                    true
                },
            )
        if (accepted) evaluator.evaluateSoon(voteId)
    }

    /** @throws ResponseStatusException 404 if there is no evaluated vote of this ID */
    fun markResultPosted(voteId: Long) {
        transactionTemplate.executeWithoutResult {
            val vote = voteRepository.findById(voteId).orElseThrow { notFound() }
            if (vote.status != VoteStatus.EVALUATED) throw notFound()
            if (vote.resultPostedAt == null) vote.resultPostedAt = clock.instant()
        }
    }

    /**
     * Gets results out that are stuck: evaluations that failed are tried again after
     * [VoteProperties.retryInterval], and the bot is asked again for the role members or to post the result after
     * [VoteProperties.botTimeout], in case it lost the event.
     */
    fun retryStuck(properties: VoteProperties) {
        val now = clock.instant()
        voteRepository.findByStatusOrderByStartedAtDesc(VoteStatus.COUNTING).forEach { vote ->
            val lastAttempt = vote.lastAttemptAt ?: Instant.MIN
            if (vote.electorateReportedAt != null) {
                if (!lastAttempt.isAfter(now - properties.retryInterval)) evaluator.evaluateSoon(requireNotNull(vote.id))
            } else if (!lastAttempt.isAfter(now - properties.botTimeout)) {
                republish(requireNotNull(vote.id)) { VoteClosedEvent(it, clock.millis()) }
            }
        }
        voteRepository
            .findByStatusOrderByStartedAtDesc(VoteStatus.EVALUATED)
            .filter { it.resultPostedAt == null && !(it.lastAttemptAt ?: Instant.MIN).isAfter(now - properties.botTimeout) }
            .forEach { vote -> republish(requireNotNull(vote.id)) { VoteEvaluatedEvent(it, clock.millis()) } }
    }

    private fun republish(
        voteId: Long,
        event: (Long) -> BitsEvent,
    ) {
        transactionTemplate.executeWithoutResult {
            val vote = voteRepository.findForUpdate(voteId) ?: return@executeWithoutResult
            vote.lastAttemptAt = clock.instant()
            eventPublisher.publish(event(voteId))
        }
    }

    private fun Vote.acceptsBallots() = status == VoteStatus.OPEN && (endsAt == null || clock.instant().isBefore(endsAt))

    /** For the audit log, readable after role and channel are gone; e.g. "„Neue Steuern?“ (@Kongress, #abstimmungen)". */
    private fun describe(vote: Vote): String =
        "„${vote.question}“ (@${vote.roleName ?: vote.roleId}, #${vote.channelName ?: vote.channelId})"

    private fun badRequest(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    companion object {
        const val MAX_QUESTION_LENGTH = 500

        /** Times in German texts are shown as the guild sees them. */
        val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        private val TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMAN).withZone(ZONE)

        fun format(instant: Instant): String = TIME.format(instant)
    }
}

/** Who starts, ends or cancels a vote: a member in Discord or a user of the dashboard. */
data class VoteManager(
    /** `discord:<id>` or `admin:<username>`, as in the audit log. */
    val subject: String,
    val name: String,
    val avatarUrl: String?,
    val admin: Boolean,
    val roleIds: List<String>,
) {
    fun auditActor() = AuditActor(subject, name, avatarUrl)
}

sealed interface StartOutcome {
    data class Started(
        val vote: Vote,
    ) : StartOutcome

    data object Forbidden : StartOutcome

    data object ChannelNotPostable : StartOutcome
}

sealed interface CloseOutcome {
    data object Closed : CloseOutcome

    data object Forbidden : CloseOutcome

    data object NotOpen : CloseOutcome
}

sealed interface CastOutcome {
    data class Cast(
        val previous: Choice?,
    ) : CastOutcome

    data object NotLinked : CastOutcome

    data object Closed : CastOutcome
}

data class Participation(
    val voted: Int,
    val eligible: Int,
)
