package eu.luftiger.bits.backend.embassy.api

import eu.luftiger.bits.backend.embassy.api.dto.EmbassyRecords
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the embassy module: what it keeps about a member, for the member's own download. */
@NamedInterface("api")
interface EmbassyMemberships {
    fun recordsAbout(discordUserId: String): EmbassyRecords
}
