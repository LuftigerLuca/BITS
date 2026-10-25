package eu.luftiger.bits.backend.account.api.dto

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
data class DepartedMember(
    val discordUserId: String,
    /** For the system log; `null` if the bot did not see the member leave. */
    val discordName: String? = null,
)
