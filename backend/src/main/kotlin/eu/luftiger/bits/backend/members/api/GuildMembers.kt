package eu.luftiger.bits.backend.members.api

import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the members module: who is on the guild right now, as far as the backend knows. */
@NamedInterface("api")
interface GuildMembers {
    /**
     * Discord IDs of the members without bots, as the bot reported them last. `null` until the bot has reported them
     * since the backend started, since the list is kept in memory only.
     */
    fun discordUserIds(): Set<String>?
}
