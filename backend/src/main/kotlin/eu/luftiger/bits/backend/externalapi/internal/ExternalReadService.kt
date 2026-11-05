package eu.luftiger.bits.backend.externalapi.internal

import eu.luftiger.bits.backend.account.api.AccountDirectory
import eu.luftiger.bits.backend.account.api.dto.AccountRecord
import eu.luftiger.bits.backend.militaryunit.api.DskDirectory
import eu.luftiger.bits.backend.overview.api.OverviewHistory
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.shared.NotFoundException
import eu.luftiger.bits.backend.vote.api.VoteHistory
import eu.luftiger.bits.backend.vote.api.dto.VoteChoice
import eu.luftiger.bits.backend.vote.api.dto.VoteRecord
import eu.luftiger.bits.backend.vote.api.dto.VoteState
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * What the third-party API reads, from the `api/` of the modules that own the data, turned into the API's own types.
 * It only reads the database, never WarEra, so a client cannot use up WarEra's budget.
 */
@Service
class ExternalReadService(
    private val accounts: AccountDirectory,
    private val overview: OverviewHistory,
    private val dsks: DskDirectory,
    private val votes: VoteHistory,
    private val clock: Clock,
) {
    fun accounts(
        page: Int,
        size: Int,
    ): PageResponse<AccountResponse> = accounts.page(page, size).toResponse { it.toResponse() }

    /** @throws NotFoundException if the Discord account is not linked */
    fun accountByDiscordId(discordUserId: String): AccountResponse =
        accounts.byDiscordUserId(discordUserId)?.toResponse() ?: throw NotFoundException("Discord account not linked")

    /** @throws NotFoundException if the WarEra account is not linked */
    fun accountByWareraId(wareraUserId: String): AccountResponse =
        accounts.byWareraUserId(wareraUserId)?.toResponse() ?: throw NotFoundException("WarEra account not linked")

    /**
     * The days from [from] to [to], by default the last [DEFAULT_DAYS] days up to today.
     *
     * @throws InvalidInputException for a malformed date, [from] after [to] or more than [MAX_DAYS] days
     */
    fun snapshots(
        from: String?,
        to: String?,
    ): SnapshotsResponse {
        val end = to?.let { date(it, "to") } ?: clock.instant().atZone(ZONE).toLocalDate()
        val start = from?.let { date(it, "from") } ?: end.minusDays(DEFAULT_DAYS - 1)
        if (start > end) throw InvalidInputException("from must not be after to")
        if (start.plusDays(MAX_DAYS) <= end) throw InvalidInputException("At most $MAX_DAYS days at once")
        return SnapshotsResponse(
            from = start.toString(),
            to = end.toString(),
            days =
                overview.snapshots(start, end).map {
                    SnapshotResponse(it.day.toString(), it.members, it.verifiedMembers, it.countryId, it.citizens, it.activeCitizens)
                },
        )
    }

    fun dsks(): List<DskResponse> =
        dsks.all().map { dsk ->
            DskResponse(
                id = dsk.id,
                name = dsk.name,
                roleId = dsk.roleId,
                militaryUnits = dsk.militaryUnits.map { DskUnitResponse(it.muId, it.name, it.assignedAt.toString()) },
            )
        }

    fun votes(
        page: Int,
        size: Int,
    ): PageResponse<VoteResponse> = votes.page(page, size).toResponse { it.toResponse() }

    /** @throws NotFoundException if there is no such vote */
    fun vote(id: Long): VoteResponse = votes.find(id)?.toResponse() ?: throw NotFoundException("No such vote")

    private fun date(
        value: String,
        name: String,
    ): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            throw InvalidInputException("$name must be a date like 2026-12-31")
        }

    private fun AccountRecord.toResponse() = AccountResponse(discordUserId, wareraUserId, wareraUsername, verifiedAt.toString())

    private fun VoteRecord.toResponse() =
        VoteResponse(
            id = id,
            question = question,
            roleName = roleName,
            state =
                when (state) {
                    VoteState.OPEN -> VoteStateResponse.OPEN
                    VoteState.COUNTING -> VoteStateResponse.COUNTING
                    VoteState.EVALUATED -> VoteStateResponse.EVALUATED
                    VoteState.CANCELLED -> VoteStateResponse.CANCELLED
                },
            startedAt = startedAt.toString(),
            endsAt = endsAt?.toString(),
            closedAt = closedAt?.toString(),
            threshold =
                thresholdPercent?.let { percent ->
                    thresholdChoice?.let {
                        ThresholdResponse(
                            percent,
                            when (it) {
                                VoteChoice.YES -> ThresholdChoice.YES
                                VoteChoice.NO -> ThresholdChoice.NO
                            },
                        )
                    }
                },
            endedByThreshold = endedByThreshold,
            result =
                result?.let { tally ->
                    VoteResultResponse(
                        yes = tally.yes,
                        no = tally.no,
                        abstain = tally.abstain,
                        voted = tally.yes + tally.no + tally.abstain,
                        eligible = tally.eligible,
                        parties =
                            tally.parties.map {
                                PartyResultResponse(
                                    it.partyId,
                                    it.partyName,
                                    it.yes,
                                    it.no,
                                    it.abstain,
                                    it.yes + it.no + it.abstain,
                                    it.eligible,
                                )
                            },
                    )
                },
        )

    private fun <T : Any, R> Page<T>.toResponse(map: (T) -> R) = PageResponse(content.map(map), number, size, totalElements, totalPages)

    companion object {
        /** The day of the German Discord. */
        private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

        const val DEFAULT_DAYS = 30L

        /** A year, also a leap year. */
        const val MAX_DAYS = 366L

        const val MAX_PAGE_SIZE = 100L

        const val DEFAULT_PAGE_SIZE = 50
    }
}
