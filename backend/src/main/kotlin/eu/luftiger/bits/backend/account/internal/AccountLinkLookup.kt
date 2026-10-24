package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.account.api.dto.LinkedWareraAccount
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class AccountLinkLookup(
    private val linkRepository: AccountLinkRepository,
) : AccountLinks {
    override fun findByDiscordUserId(discordUserId: String): LinkedWareraAccount? =
        linkRepository.findByIdOrNull(discordUserId)?.let { LinkedWareraAccount(it.wareraUserId, it.wareraUsername) }
}
