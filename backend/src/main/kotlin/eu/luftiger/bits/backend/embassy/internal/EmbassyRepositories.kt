package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.embassy.domain.DiscordThread
import eu.luftiger.bits.backend.embassy.domain.EmbassyHostRole
import eu.luftiger.bits.backend.embassy.domain.EmbassyManualMember
import eu.luftiger.bits.backend.embassy.domain.EmbassyOffice
import eu.luftiger.bits.backend.embassy.domain.EmbassySetting
import eu.luftiger.bits.backend.embassy.domain.EmbassyThread
import eu.luftiger.bits.backend.embassy.domain.EmbassyThreadMember
import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface DiscordThreadRepository : JpaRepository<DiscordThread, String>

interface EmbassyThreadRepository : JpaRepository<EmbassyThread, String> {
    fun existsByCountryId(countryId: String): Boolean
}

interface EmbassyManualMemberRepository : JpaRepository<EmbassyManualMember, Long> {
    fun findByDiscordUserId(discordUserId: String): List<EmbassyManualMember>

    fun findByThreadIdAndDiscordUserId(
        threadId: String,
        discordUserId: String,
    ): EmbassyManualMember?
}

interface EmbassyThreadMemberRepository : JpaRepository<EmbassyThreadMember, Long> {
    fun findByDiscordUserId(discordUserId: String): List<EmbassyThreadMember>

    @Modifying
    @Query("DELETE FROM EmbassyThreadMember m WHERE m.threadId = :threadId")
    fun deleteByThreadId(threadId: String)
}

interface EmbassyOfficeRepository : JpaRepository<EmbassyOffice, GovernmentOffice>

interface EmbassyHostRoleRepository : JpaRepository<EmbassyHostRole, String>

interface EmbassySettingRepository : JpaRepository<EmbassySetting, Short>
