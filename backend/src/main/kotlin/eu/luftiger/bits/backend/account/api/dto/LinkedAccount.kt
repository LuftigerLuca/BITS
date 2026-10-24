package eu.luftiger.bits.backend.account.api.dto

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
data class LinkedAccount(
    val discordUserId: String,
    val wareraUserId: String,
)
