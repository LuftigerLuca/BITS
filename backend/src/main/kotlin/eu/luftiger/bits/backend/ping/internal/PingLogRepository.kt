package eu.luftiger.bits.backend.ping.internal

import eu.luftiger.bits.backend.ping.domain.PingLog
import org.springframework.data.jpa.repository.JpaRepository

interface PingLogRepository : JpaRepository<PingLog, Long>
