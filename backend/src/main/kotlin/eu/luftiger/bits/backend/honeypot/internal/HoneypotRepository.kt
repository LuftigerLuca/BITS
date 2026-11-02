package eu.luftiger.bits.backend.honeypot.internal

import eu.luftiger.bits.backend.honeypot.domain.HoneypotSetting
import org.springframework.data.jpa.repository.JpaRepository

interface HoneypotRepository : JpaRepository<HoneypotSetting, Short>
