package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.api.NicknameOptIns
import eu.luftiger.bits.backend.account.api.NicknameOptedIn
import eu.luftiger.bits.backend.account.domain.NicknameOptIn
import eu.luftiger.bits.shared.NicknamePreferenceResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Members opt in to their WarEra name as nickname themselves, also before they link. Opting out only stops BITS from
 * changing the nickname; it stays as it is.
 */
@Service
class NicknamePreferenceService(
    private val optInRepository: NicknameOptInRepository,
    private val linkRepository: AccountLinkRepository,
    private val applicationEvents: ApplicationEventPublisher,
    private val clock: Clock,
) : NicknameOptIns {
    override fun discordUserIds(): Set<String> = optInRepository.findAll().mapTo(HashSet()) { it.discordUserId }

    /** Turning it on syncs a linked member right away, so the nickname does not wait for the next hour. */
    @Transactional
    fun set(
        discordUserId: String,
        enabled: Boolean,
    ): NicknamePreferenceResponse {
        val link = linkRepository.findByIdOrNull(discordUserId)
        val existing = optInRepository.findByIdOrNull(discordUserId)
        if (enabled && existing == null) {
            optInRepository.save(NicknameOptIn(discordUserId, clock.instant()))
            if (link != null) applicationEvents.publishEvent(NicknameOptedIn(discordUserId))
        } else if (!enabled && existing != null) {
            optInRepository.delete(existing)
        }
        return NicknamePreferenceResponse(enabled, link?.wareraUsername)
    }
}
