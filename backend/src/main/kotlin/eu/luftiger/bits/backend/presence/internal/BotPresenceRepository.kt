package eu.luftiger.bits.backend.presence.internal

import eu.luftiger.bits.backend.presence.domain.BotPresenceSetting
import org.springframework.data.jpa.repository.JpaRepository

interface BotPresenceRepository : JpaRepository<BotPresenceSetting, Short>
