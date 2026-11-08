package eu.luftiger.bits.backend.reprimand.api

import eu.luftiger.bits.backend.reprimand.api.dto.MemberReprimands
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the reprimand module: what it keeps about a member, for the member's own download. */
@NamedInterface("api")
interface ReprimandRecords {
    fun recordsAbout(discordUserId: String): MemberReprimands
}
