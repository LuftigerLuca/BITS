package eu.luftiger.bits.backend.ping.internal

import eu.luftiger.bits.backend.ping.domain.PingLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface PingLogRepository : JpaRepository<PingLog, Long> {
    fun findByDiscordUserIdOrderById(discordUserId: String): List<PingLog>

    @Modifying
    @Query("DELETE FROM PingLog p WHERE p.createdAt < :before")
    fun deleteCreatedBefore(before: Instant): Int
}
