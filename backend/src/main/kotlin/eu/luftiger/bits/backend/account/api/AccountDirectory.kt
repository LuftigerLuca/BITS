package eu.luftiger.bits.backend.account.api

import eu.luftiger.bits.backend.account.api.dto.AccountRecord
import org.springframework.data.domain.Page
import org.springframework.modulith.NamedInterface

/** Read access to the linked accounts for the third-party API: single lookups and the whole list, page by page. */
@NamedInterface("api")
interface AccountDirectory {
    fun byDiscordUserId(discordUserId: String): AccountRecord?

    fun byWareraUserId(wareraUserId: String): AccountRecord?

    /** In the order they were linked, the oldest first. */
    fun page(
        page: Int,
        size: Int,
    ): Page<AccountRecord>
}
