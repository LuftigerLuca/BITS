package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.shared.NotFoundException
import eu.luftiger.bits.backend.vote.domain.Choice
import eu.luftiger.bits.backend.vote.domain.Elector
import eu.luftiger.bits.backend.vote.domain.Vote
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import eu.luftiger.bits.shared.VoteClosedEvent
import eu.luftiger.bits.shared.VoteStartedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
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
    private val managerRoles: VoteManagerRoles,
    private val channelRepository: DiscordChannelRepository,
    private val guildRoles: GuildRoles,
    private val accountLinks: AccountLinks,
    private val eventPublisher: EventPublisher,
    private val auditLog: AuditLog,
    private val evaluator: VoteEvaluator,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /**
     * [threshold] ends the vote early once more than its share of the eligible members chose its option, see
     * [participation].
     *
     * @throws InvalidInputException if the question is empty or too long, the end has passed, the threshold is
     * not 1 to 99 percent for Yes or No, or the role is unknown
     */
    fun start(
        manager: VoteManager,
        question: String,
        roleId: String,
        channelId: String,
        endsAt: Instant?,
        threshold: Threshold? = null,
    ): StartOutcome {
        if (!managerRoles.mayManage(manager)) return StartOutcome.Forbidden
        val text = question.trim()
        if (text.isEmpty() ||
            text.length > MAX_QUESTION_LENGTH
        ) {
            throw InvalidInputException("Question must have 1 to $MAX_QUESTION_LENGTH characters")
        }
        if (endsAt != null && !endsAt.isAfter(clock.instant())) throw InvalidInputException("The end has passed already")
        if (threshold != null && threshold.percent !in 1..99) throw InvalidInputException("Threshold must be 1 to 99 percent")
        if (threshold != null && threshold.choice == Choice.ABSTAIN) throw InvalidInputException("Threshold must be for Yes or No")
        val role = guildRoles.all().firstOrNull { it.id == roleId } ?: throw InvalidInputException("Unknown role $roleId")
        val channel = channelRepository.findById(channelId).orElse(null)
        if (channel == null || !channel.postable) return StartOutcome.ChannelNotPostable
        val vote =
            requireNotNull(
                transactionTemplate.execute {
                    val saved =
                        voteRepository.save(
                            Vote(
                                text,
                                role.id,
                                role.name,
                                channel.id,
                                channel.name,
                                clock.instant(),
                                manager.name,
                                endsAt,
                                threshold?.percent,
                                threshold?.choice,
                            ),
                        )
                    val end =
                        listOfNotNull(
                            endsAt?.let { "bis ${format(it)}" } ?: "ohne Enddatum",
                            threshold?.let { "endet automatisch, sobald mehr als ${it.percent} % mit ${label(it.choice)} stimmen" },
                        ).joinToString()
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
        if (manager != null && !managerRoles.mayManage(manager)) return CloseOutcome.Forbidden
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
     * do not count, unless they voted before they lost it. Ends an open vote like its end date does once it reaches
     * [Vote.thresholdPercent] for [Vote.thresholdChoice]; the bot reports the participation after each vote, so this
     * is where it is checked.
     *
     * @throws NotFoundException if there is no such vote
     */
    fun participation(
        voteId: Long,
        roleMemberIds: Collection<String>,
    ): Participation {
        var reached = false
        val participation =
            requireNotNull(
                transactionTemplate.execute {
                    val vote = voteRepository.findById(voteId).orElseThrow { NotFoundException() }
                    val choices = ballotRepository.findByVoteId(voteId).associate { it.discordUserId to it.choice }
                    val voters = choices.keys
                    val linked = accountLinks.findAll().mapTo(HashSet()) { it.discordUserId }
                    val members = roleMemberIds.toSet()
                    val participation =
                        Participation(
                            voted = members.count { it in voters },
                            eligible = members.count { it in linked || it in voters },
                        )
                    if (vote.status == VoteStatus.OPEN) {
                        vote.eligibleCount = participation.eligible
                        val choice = vote.thresholdChoice
                        reached = choice != null && vote.reachesThreshold(members.count { choices[it] == choice }, participation.eligible)
                    }
                    participation
                },
            )
        // Closing locks the vote and checks again that it is open, so a vote ended meanwhile is not closed twice.
        if (reached) close(voteId, manager = null, cancel = false)
        return participation
    }

    /** @throws NotFoundException if there is no such vote */
    fun find(voteId: Long): Vote = voteRepository.findById(voteId).orElseThrow { NotFoundException() }

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

    /** @throws NotFoundException if there is no evaluated vote of this ID */
    fun result(voteId: Long): Pair<Vote, VoteResult> {
        val vote = find(voteId)
        return vote to (results(listOf(vote))[voteId] ?: throw NotFoundException())
    }

    /** @throws NotFoundException if there is no such vote */
    fun reportMessage(
        voteId: Long,
        messageId: String,
    ) {
        transactionTemplate.executeWithoutResult {
            voteRepository.findById(voteId).orElseThrow { NotFoundException() }.messageId = messageId
        }
    }

    /**
     * Keeps the members of the role at the end and starts the evaluation. A second report, e.g. after the backend
     * asked again, changes nothing.
     *
     * @throws NotFoundException if there is no such vote
     */
    fun reportElectorate(
        voteId: Long,
        electors: List<Elector>,
    ) {
        val accepted =
            requireNotNull(
                transactionTemplate.execute {
                    val vote = voteRepository.findForUpdate(voteId) ?: throw NotFoundException()
                    if (vote.status != VoteStatus.COUNTING || vote.electorateReportedAt != null) return@execute false
                    electorRepository.saveAll(electors.distinctBy { it.discordUserId })
                    vote.electorateReportedAt = clock.instant()
                    true
                },
            )
        if (accepted) evaluator.evaluateSoon(voteId)
    }

    /** @throws NotFoundException if there is no evaluated vote of this ID */
    fun markResultPosted(voteId: Long) {
        transactionTemplate.executeWithoutResult {
            val vote = voteRepository.findById(voteId).orElseThrow { NotFoundException() }
            if (vote.status != VoteStatus.EVALUATED) throw NotFoundException()
            if (vote.resultPostedAt == null) vote.resultPostedAt = clock.instant()
        }
    }

    private fun Vote.acceptsBallots() = status == VoteStatus.OPEN && (endsAt == null || clock.instant().isBefore(endsAt))

    /** For the audit log, readable after role and channel are gone; e.g. "„Neue Steuern?“ (@Kongress, #abstimmungen)". */
    private fun describe(vote: Vote): String =
        "„${vote.question}“ (@${vote.roleName ?: vote.roleId}, #${vote.channelName ?: vote.channelId})"

    private fun label(choice: Choice) =
        when (choice) {
            Choice.YES -> "Ja"
            Choice.NO -> "Nein"
            Choice.ABSTAIN -> "Enthaltung"
        }

    companion object {
        const val MAX_QUESTION_LENGTH = 500

        /** Times in German texts are shown as the guild sees them. */
        val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        private val TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMAN).withZone(ZONE)

        fun format(instant: Instant): String = TIME.format(instant)
    }
}
