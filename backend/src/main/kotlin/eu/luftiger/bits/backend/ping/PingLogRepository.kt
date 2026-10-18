package eu.luftiger.bits.backend.ping

import org.springframework.data.jpa.repository.JpaRepository

interface PingLogRepository : JpaRepository<PingLog, Long>
