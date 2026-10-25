package eu.luftiger.bits.backend.account.api

import org.springframework.modulith.NamedInterface

/** Application event, published within the transaction that deletes the link of a Discord account. */
@NamedInterface("api")
data class AccountUnlinked(
    val discordUserId: String,
    /** The WarEra account the link was for; `null` from a publication stored before it was added. */
    val wareraUserId: String? = null,
    /** WarEra name at the time of linking, like [eu.luftiger.bits.backend.account.api.dto.LinkedWareraAccount]. */
    val wareraUsername: String? = null,
)
