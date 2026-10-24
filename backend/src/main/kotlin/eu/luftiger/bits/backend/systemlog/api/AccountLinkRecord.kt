package eu.luftiger.bits.backend.systemlog.api

import org.springframework.modulith.NamedInterface

/** A Discord account was linked to a WarEra account; see [SystemLog.recordAccountLinked]. */
@NamedInterface("api")
data class AccountLinkRecord(
    val discordUserId: String,
    /** As the bot reported it with the check; `null` from an older bot. */
    val discordName: String?,
    val wareraUserId: String,
    val wareraUsername: String,
    /** Discord account the WarEra account was linked to before, if it was another one. */
    val previousDiscordUserId: String?,
)
