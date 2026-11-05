package eu.luftiger.bits.backend.externalapi.internal

import eu.luftiger.bits.backend.externalapi.domain.ApiToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface ApiTokenRepository : JpaRepository<ApiToken, Long> {
    fun findByTokenHash(tokenHash: String): ApiToken?

    fun findAllByOrderByCreatedAtDesc(): List<ApiToken>

    /**
     * Sets the time of last use, unless it is more recent than [notAfter]: a client calling many times a minute causes
     * one write, not one per request.
     */
    @Modifying
    @Query(
        "UPDATE ApiToken t SET t.lastUsedAt = :now WHERE t.id = :id AND (t.lastUsedAt IS NULL OR t.lastUsedAt < :notAfter)",
    )
    fun touch(
        id: Long,
        now: Instant,
        notAfter: Instant,
    ): Int
}
