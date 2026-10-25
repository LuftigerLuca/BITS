package eu.luftiger.bits.backend.account.api

import org.springframework.modulith.NamedInterface

/** Application event, published within the transaction that stores the opt-in of a linked member. */
@NamedInterface("api")
data class NicknameOptedIn(
    val discordUserId: String,
)
