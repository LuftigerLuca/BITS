package eu.luftiger.bits.backend.members.api

import eu.luftiger.bits.backend.members.api.dto.GuildMember
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the members module: who is on the guild right now, as far as the backend knows. */
@NamedInterface("api")
interface GuildMembers {
    /**
     * Discord IDs of the members without bots, as the bot reported them last. `null` until the bot has reported them
     * since the backend started, since the list is kept in memory only.
     */
    fun discordUserIds(): Set<String>?

    /**
     * The same members with their roles. `null` until the bot has reported them since the backend started, and as long
     * as it is a bot that reports no roles yet.
     */
    fun members(): Collection<GuildMember>?
}
