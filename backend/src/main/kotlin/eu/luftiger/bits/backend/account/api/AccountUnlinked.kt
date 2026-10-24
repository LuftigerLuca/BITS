package eu.luftiger.bits.backend.account.api

import org.springframework.modulith.NamedInterface

/** Application event, published within the transaction that deletes the link of a Discord account. */
@NamedInterface("api")
data class AccountUnlinked(
    val discordUserId: String,
)
