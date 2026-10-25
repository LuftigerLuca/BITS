package eu.luftiger.bits.backend.systemlog.api

import org.springframework.modulith.NamedInterface

/** The link of a member who left the guild was deleted; see [SystemLog.recordAccountRemoved]. */
@NamedInterface("api")
data class AccountRemovalRecord(
    val discordUserId: String,
    /** As the bot reported it with the departure; `null` if the bot was offline when the member left. */
    val discordName: String?,
    val wareraUserId: String,
    val wareraUsername: String,
)
