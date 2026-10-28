package eu.luftiger.bits.backend.vote.api

import eu.luftiger.bits.backend.vote.api.dto.GuildChannel
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the vote module: the text channels of the guild, as the bot reported them last. */
@NamedInterface("api")
interface GuildChannels {
    /** In the order Discord lists them. */
    fun all(): List<GuildChannel>
}
