package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.domain.Ballot
import eu.luftiger.bits.backend.vote.domain.DiscordChannel
import eu.luftiger.bits.backend.vote.domain.Elector
import eu.luftiger.bits.backend.vote.domain.NonVoter
import eu.luftiger.bits.backend.vote.domain.PartyColor
import eu.luftiger.bits.backend.vote.domain.PartyResult
import eu.luftiger.bits.backend.vote.domain.Vote
import eu.luftiger.bits.backend.vote.domain.VoteManagerRole
import eu.luftiger.bits.backend.vote.domain.VoteResultSetting
import eu.luftiger.bits.backend.vote.domain.VoteStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface VoteRepository : JpaRepository<Vote, Long> {
    fun findByStatusOrderByStartedAtDesc(status: VoteStatus): List<Vote>

    fun findByStatusInOrderByClosedAtDesc(statuses: Collection<VoteStatus>): List<Vote>

    /** Waits for ballots being cast, which hold [findForBallot], so none slips in after the vote is closed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vote v WHERE v.id = :id")
    fun findForUpdate(id: Long): Vote?

    /** Shared, so ballots do not wait for each other, only for a vote being closed. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT v FROM Vote v WHERE v.id = :id")
    fun findForBallot(id: Long): Vote?

    @Query("SELECT v.id FROM Vote v WHERE v.status = 'OPEN' AND v.endsAt <= :now")
    fun findDueIds(now: Instant): List<Long>
}

interface BallotRepository : JpaRepository<Ballot, Long> {
    /**
     * Stores the member's vote, replacing an earlier one in one step, so two quick clicks cannot both insert.
     *
     * @return the choice before, `null` for a first vote
     */
    @Query(
        value = """
            WITH previous AS (SELECT choice FROM vote_ballot WHERE vote_id = :voteId AND discord_user_id = :discordUserId),
                 upserted AS (
                     INSERT INTO vote_ballot (vote_id, discord_user_id, warera_user_id, choice, cast_at)
                     VALUES (:voteId, :discordUserId, :wareraUserId, :choice, :castAt)
                     ON CONFLICT (vote_id, discord_user_id)
                         DO UPDATE SET warera_user_id = excluded.warera_user_id, choice = excluded.choice, cast_at = excluded.cast_at
                     RETURNING 1)
            SELECT (SELECT choice FROM previous) FROM upserted
        """,
        nativeQuery = true,
    )
    fun cast(
        voteId: Long,
        discordUserId: String,
        wareraUserId: String,
        choice: String,
        castAt: Instant,
    ): String?

    fun findByVoteId(voteId: Long): List<Ballot>

    @Query("SELECT COUNT(b) FROM Ballot b WHERE b.voteId = :voteId AND b.discordUserId IN :discordUserIds")
    fun countByVoteIdAndDiscordUserIdIn(
        voteId: Long,
        discordUserIds: Collection<String>,
    ): Long

    fun countByVoteId(voteId: Long): Long

    @Modifying
    @Query("DELETE FROM Ballot b WHERE b.voteId = :voteId")
    fun deleteByVoteId(voteId: Long)
}

interface ElectorRepository : JpaRepository<Elector, Long> {
    fun findByVoteId(voteId: Long): List<Elector>

    @Modifying
    @Query("DELETE FROM Elector e WHERE e.voteId = :voteId")
    fun deleteByVoteId(voteId: Long)
}

interface PartyResultRepository : JpaRepository<PartyResult, Long> {
    fun findByVoteIdOrderByPosition(voteId: Long): List<PartyResult>

    fun findByVoteIdInOrderByPosition(voteIds: Collection<Long>): List<PartyResult>
}

interface NonVoterRepository : JpaRepository<NonVoter, Long> {
    fun findByVoteIdOrderByPosition(voteId: Long): List<NonVoter>

    fun findByVoteIdInOrderByPosition(voteIds: Collection<Long>): List<NonVoter>
}

interface VoteManagerRoleRepository : JpaRepository<VoteManagerRole, String>

interface DiscordChannelRepository : JpaRepository<DiscordChannel, String>

interface VoteResultSettingRepository : JpaRepository<VoteResultSetting, Short> {
    /** Without loading the picture. */
    @Query("SELECT s.noPartyColor FROM VoteResultSetting s")
    fun noPartyColor(): Int?

    @Query("SELECT s.background FROM VoteResultSetting s")
    fun background(): ByteArray?
}

interface PartyColorRepository : JpaRepository<PartyColor, String> {
    fun findByCountryId(countryId: String): List<PartyColor>

    @Modifying
    @Query("DELETE FROM PartyColor c WHERE c.countryId = :countryId")
    fun deleteByCountryId(countryId: String)
}
