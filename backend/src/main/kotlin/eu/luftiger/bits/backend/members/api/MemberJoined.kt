package eu.luftiger.bits.backend.members.api

import org.springframework.modulith.NamedInterface

/** Application event, published within a transaction of its own when the bot reports a member who joined the guild. */
@NamedInterface("api")
data class MemberJoined(
    val discordUserId: String,
)
