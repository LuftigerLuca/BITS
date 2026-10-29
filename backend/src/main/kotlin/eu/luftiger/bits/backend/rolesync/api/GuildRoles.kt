package eu.luftiger.bits.backend.rolesync.api

import eu.luftiger.bits.backend.rolesync.api.dto.GuildRole
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the rolesync module: the roles of the guild, as the bot reported them last. */
@NamedInterface("api")
interface GuildRoles {
    /** Highest first, without `@everyone`. */
    fun all(): List<GuildRole>

    /** The roles the bot can give, highest first. */
    fun assignable(): List<GuildRole>

    /** IDs of the roles BITS manages, i.e. those with an active rule or of a DSK, highest first. */
    fun managedIds(): List<String>

    /** IDs of the roles that have a rule, whether it runs or not; such a role cannot belong to a DSK. */
    fun withRuleIds(): Set<String>
}
