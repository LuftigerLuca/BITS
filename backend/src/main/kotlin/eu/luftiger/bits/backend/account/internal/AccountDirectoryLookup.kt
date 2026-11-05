package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.api.AccountDirectory
import eu.luftiger.bits.backend.account.api.dto.AccountRecord
import eu.luftiger.bits.backend.account.domain.AccountLink
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class AccountDirectoryLookup(
    private val linkRepository: AccountLinkRepository,
) : AccountDirectory {
    override fun byDiscordUserId(discordUserId: String): AccountRecord? = linkRepository.findByIdOrNull(discordUserId)?.toRecord()

    override fun byWareraUserId(wareraUserId: String): AccountRecord? = linkRepository.findByWareraUserId(wareraUserId)?.toRecord()

    override fun page(
        page: Int,
        size: Int,
    ): Page<AccountRecord> =
        // The Discord ID breaks ties, so that no account shows up on two pages.
        linkRepository.findAll(PageRequest.of(page, size, Sort.by("verifiedAt", "discordUserId"))).map { it.toRecord() }

    private fun AccountLink.toRecord() = AccountRecord(discordUserId, wareraUserId, wareraUsername, verifiedAt)
}
