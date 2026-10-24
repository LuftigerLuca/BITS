package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.domain.AccountVerification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface AccountVerificationRepository : JpaRepository<AccountVerification, String> {
    /**
     * Marks [code] as checked at [now], unless it was checked after [checkedBefore] or is no longer the user's current
     * code. Returns the number of rows changed, so of two checks at the same time only one gets 1.
     */
    @Modifying
    @Query(
        """
        UPDATE AccountVerification v SET v.lastCheckedAt = :now
        WHERE v.discordUserId = :discordUserId AND v.code = :code
          AND (v.lastCheckedAt IS NULL OR v.lastCheckedAt <= :checkedBefore)
        """,
    )
    fun claimCheck(
        discordUserId: String,
        code: String,
        now: Instant,
        checkedBefore: Instant,
    ): Int

    @Modifying
    @Query("DELETE FROM AccountVerification v WHERE v.expiresAt < :before")
    fun deleteExpiredBefore(before: Instant): Int
}
