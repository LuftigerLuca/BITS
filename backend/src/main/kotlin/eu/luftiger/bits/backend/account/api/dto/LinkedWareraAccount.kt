package eu.luftiger.bits.backend.account.api.dto

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
data class LinkedWareraAccount(
    val wareraUserId: String,
    /** Username at the time of linking; WarEra users can rename themselves. */
    val wareraUsername: String,
)
