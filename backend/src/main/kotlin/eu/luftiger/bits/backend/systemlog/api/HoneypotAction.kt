package eu.luftiger.bits.backend.systemlog.api

import org.springframework.modulith.NamedInterface

/** What the bot did about a member's message in the honeypot; see [SystemLog.recordHoneypotAction]. */
@NamedInterface("api")
data class HoneypotAction(
    val discordUserId: String,
    val memberName: String,
    val outcome: HoneypotActionOutcome,
    /** Why the member was not kicked and what else did not work, in German. */
    val reason: String?,
)

@NamedInterface("api")
enum class HoneypotActionOutcome {
    KICKED,
    SKIPPED,
    FAILED,
}
