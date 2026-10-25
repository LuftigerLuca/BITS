package eu.luftiger.bits.backend.account.api

import eu.luftiger.bits.backend.account.api.dto.StoredAccountData
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the account module for members who look after their own account in the dashboard. */
@NamedInterface("api")
interface AccountSelfService {
    /** Everything the account module keeps about [discordUserId]. */
    fun storedData(discordUserId: String): StoredAccountData

    /** Works like `/unverify`, see [AccountUnlinked]; returns whether there was a link to delete. */
    fun unlink(discordUserId: String): Boolean
}
