package eu.luftiger.bits.backend.rolesync.api

import eu.luftiger.bits.backend.rolesync.api.dto.GuildRole
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the rolesync module: the roles of the guild, as the bot reported them last. */
@NamedInterface("api")
interface GuildRoles {
    /** Highest first, without `@everyone`. */
    fun all(): List<GuildRole>

    /** IDs of the roles BITS manages, i.e. those with an active rule, highest first. */
    fun managedIds(): List<String>
}
