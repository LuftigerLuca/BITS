package eu.luftiger.bits.backend.reprimand.internal

import eu.luftiger.bits.backend.reprimand.domain.Reprimand
import eu.luftiger.bits.backend.reprimand.domain.ReprimandRole
import eu.luftiger.bits.backend.reprimand.domain.ReprimandSetting
import org.springframework.data.jpa.repository.JpaRepository

interface ReprimandRepository : JpaRepository<Reprimand, Long> {
    fun findByDiscordUserIdOrderById(discordUserId: String): List<Reprimand>

    fun findByActorDiscordUserIdOrderById(actorDiscordUserId: String): List<Reprimand>

    fun countByDiscordUserIdAndWithdrawnAtIsNull(discordUserId: String): Int
}

interface ReprimandRoleRepository : JpaRepository<ReprimandRole, String>

interface ReprimandSettingRepository : JpaRepository<ReprimandSetting, Short>
