package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.rolesync.api.MemberCountries
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.backend.vote.domain.Ballot
import eu.luftiger.bits.backend.vote.domain.Choice
import eu.luftiger.bits.backend.vote.domain.Elector
import eu.luftiger.bits.backend.vote.domain.NonVoter
import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraNotFoundException
import eu.luftiger.bits.warera.WareraParty
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.text.Collator
import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Works out the result of a vote once the bot has reported the members of the role at the end, on a thread of its own,
 * one vote at a time. The country of everybody who could vote is taken from the last role sync, see [MemberCountries],
 * so it may be up to an hour old; only users the sync did not load are loaded from WarEra, about one per second.
 *
 * Eligible are the members of the role at the end who are linked, or who voted while they were; the ballots of members
 * who lost the role are dropped. Everybody counts for the party of their country of citizenship they are a member of,
 * as WarEra listed them up to ten minutes ago (the parties come from the cache of the WarEra client), or as in no party.
 *
 * The parties' counts and the names of the non-voters are stored, and the ballots and the role members deleted, in the
 * transaction that publishes the [VoteEvaluatedEvent]. If WarEra cannot be reached, nothing changes and
 * [VoteService.retryStuck] tries again later; the first failure of a vote is recorded in the [SystemLog].
 */
@Component
class VoteEvaluator(
    private val voteRepository: VoteRepository,
    private val ballotRepository: BallotRepository,
    private val electorRepository: ElectorRepository,
    private val partyResultRepository: PartyResultRepository,
    private val nonVoterRepository: NonVoterRepository,
    private val accountLinks: AccountLinks,
    private val memberCountries: MemberCountries,
    private val wareraClient: WareraClient,
    private val eventPublisher: EventPublisher,
    private val systemLog: SystemLog,
    private val transactionTemplate: TransactionTemplate,
    private val properties: VoteProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "vote-evaluation") }

    /** Votes waiting for the thread, so the retry job does not queue one twice. */
    private val queued = ConcurrentHashMap.newKeySet<Long>()

    fun evaluateSoon(voteId: Long) {
        if (!queued.add(voteId)) return
        executor.execute {
            try {
                evaluate(voteId)
            } catch (e: Exception) {
                log.error("Evaluation of vote {} failed", voteId, e)
            } finally {
                queued.remove(voteId)
            }
        }
    }

    /** Waits until all evaluations started so far have finished, for tests. */
    fun awaitIdle(timeout: Duration = Duration.ofSeconds(10)) {
        executor.submit {}.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun evaluate(voteId: Long) {
        val snapshot =
            transactionTemplate.execute {
                val vote = voteRepository.findForUpdate(voteId)
                if (vote == null || vote.status != VoteStatus.COUNTING || vote.electorateReportedAt == null) return@execute null
                vote.lastAttemptAt = clock.instant()
                Snapshot(vote.question, electorRepository.findByVoteId(voteId), ballotRepository.findByVoteId(voteId))
            } ?: return
        val tally =
            try {
                tally(snapshot)
            } catch (e: WareraApiException) {
                log.warn("Vote {} cannot be evaluated, WarEra not reachable; trying again later", voteId, e)
                recordDelay(voteId, snapshot.question, e)
                return
            }
        transactionTemplate.executeWithoutResult {
            val vote = voteRepository.findForUpdate(voteId)
            if (vote == null || vote.status != VoteStatus.COUNTING) return@executeWithoutResult
            partyResultRepository.saveAll(
                tally.parties.mapIndexed { index, party ->
                    PartyResult(voteId, index, party.name, party.yes, party.no, party.abstain, party.eligible, party.id)
                },
            )
            nonVoterRepository.saveAll(tally.nonVoters.mapIndexed { index, name -> NonVoter(voteId, index, name) })
            ballotRepository.deleteByVoteId(voteId)
            electorRepository.deleteByVoteId(voteId)
            val now = clock.instant()
            vote.status = VoteStatus.EVALUATED
            vote.evaluatedAt = now
            vote.lastAttemptAt = now
            eventPublisher.publish(VoteEvaluatedEvent(voteId, clock.millis()))
        }
        log.info("Vote {} evaluated: {} parties, {} non-voters", voteId, tally.parties.size, tally.nonVoters.size)
    }

    private fun tally(snapshot: Snapshot): Tally {
        val ballots = snapshot.ballots.associateBy { it.discordUserId }
        // The ballots of members who lost the role or left are not among the electors and drop out here.
        val eligible =
            snapshot.electors.mapNotNull { elector ->
                val ballot = ballots[elector.discordUserId]
                val wareraUserId = ballot?.wareraUserId ?: accountLinks.findByDiscordUserId(elector.discordUserId)?.wareraUserId
                wareraUserId?.let { Eligible(elector.name, it, ballot?.choice) }
            }
        val parties = parties(eligible.map { it.wareraUserId }.distinct())
        val groups = eligible.groupBy { parties[it.wareraUserId] }
        val collator = Collator.getInstance(Locale.GERMAN)
        val partyTallies =
            groups.entries
                .sortedWith(
                    compareBy<Map.Entry<WareraParty?, List<Eligible>>> { it.key == null }
                        .thenByDescending { it.value.size }
                        .thenBy(collator) { it.key?.name.orEmpty() },
                ).map { (party, members) ->
                    PartyTally(
                        id = party?.id,
                        name = party?.name,
                        yes = members.count { it.choice == Choice.YES },
                        no = members.count { it.choice == Choice.NO },
                        abstain = members.count { it.choice == Choice.ABSTAIN },
                        eligible = members.size,
                    )
                }
        val nonVoters = eligible.filter { it.choice == null }.map { it.name }.sortedWith(collator)
        return Tally(partyTallies, nonVoters)
    }

    /**
     * The party of each user among the parties of their country; users WarEra no longer knows count as in no party.
     *
     * @throws WareraApiException if WarEra cannot be reached
     */
    private fun parties(wareraUserIds: List<String>): Map<String, WareraParty> {
        val synced = memberCountries.byWareraUserId()
        val countries = HashMap<String, String?>()
        wareraUserIds.filter { it in synced }.associateWithTo(countries) { synced[it] }
        wareraUserIds.filter { it !in synced }.forEachIndexed { index, id ->
            if (index > 0 && !properties.userLookupInterval.isZero) Thread.sleep(properties.userLookupInterval)
            countries[id] =
                try {
                    wareraClient.getUser(id).country
                } catch (_: WareraNotFoundException) {
                    null
                }
        }
        val partiesByCountry =
            countries.values
                .filterNotNull()
                .distinct()
                .associateWith(wareraClient::getParties)
        return countries
            .mapNotNull { (userId, country) ->
                partiesByCountry[country]?.firstOrNull { userId in it.members }?.let { userId to it }
            }.toMap()
    }

    private fun recordDelay(
        voteId: Long,
        question: String,
        e: Exception,
    ) {
        try {
            transactionTemplate.executeWithoutResult {
                val vote = voteRepository.findForUpdate(voteId) ?: return@executeWithoutResult
                if (vote.problemRecorded) return@executeWithoutResult
                vote.problemRecorded = true
                systemLog.recordProblem(SystemProblem.VoteEvaluationDelayed(question, e.message ?: e::class.simpleName.orEmpty()))
            }
        } catch (logFailure: Exception) {
            log.warn("Could not record the delayed evaluation of vote {} in the system log", voteId, logFailure)
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private class Snapshot(
        val question: String,
        val electors: List<Elector>,
        val ballots: List<Ballot>,
    )

    private class Eligible(
        val name: String,
        val wareraUserId: String,
        val choice: Choice?,
    )

    private class PartyTally(
        val id: String?,
        val name: String?,
        val yes: Int,
        val no: Int,
        val abstain: Int,
        val eligible: Int,
    )

    private class Tally(
        val parties: List<PartyTally>,
        val nonVoters: List<String>,
    )
}
