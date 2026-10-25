package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.embassy.api.EmbassyMemberships
import eu.luftiger.bits.backend.embassy.api.dto.EmbassyRecords
import eu.luftiger.bits.backend.embassy.api.dto.ManualMembership
import eu.luftiger.bits.backend.embassy.api.dto.ReportedMembership
import org.springframework.stereotype.Component

@Component
class EmbassyMembershipLookup(
    private val threadRepository: EmbassyThreadRepository,
    private val manualMemberRepository: EmbassyManualMemberRepository,
    private val threadMemberRepository: EmbassyThreadMemberRepository,
) : EmbassyMemberships {
    override fun recordsAbout(discordUserId: String): EmbassyRecords {
        val threads = threadRepository.findAll().associateBy { it.threadId }
        return EmbassyRecords(
            manual =
                manualMemberRepository.findByDiscordUserId(discordUserId).mapNotNull { member ->
                    threads[member.threadId]?.let { ManualMembership(it.countryName, it.threadName, member.memberName, member.addedAt) }
                },
            reported =
                threadMemberRepository.findByDiscordUserId(discordUserId).mapNotNull { member ->
                    threads[member.threadId]?.let { ReportedMembership(it.countryName, it.threadName, member.name, member.reason) }
                },
        )
    }
}
