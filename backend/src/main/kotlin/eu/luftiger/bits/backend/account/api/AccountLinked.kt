package eu.luftiger.bits.backend.account.api

import org.springframework.modulith.NamedInterface

/** Application event, published within the transaction that links a Discord account to a WarEra account. */
@NamedInterface("api")
data class AccountLinked(
    val discordUserId: String,
    val wareraUserId: String,
    /** Discord account the WarEra account was linked to before, if it was another one. */
    val previousDiscordUserId: String?,
)
