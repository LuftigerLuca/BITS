package eu.luftiger.bits.backend.vote.api.dto

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
data class GuildChannel(
    val id: String,
    val name: String,
    /** `null` for none. */
    val category: String?,
    /** Whether the bot may create private threads there and manage them. */
    val threadsCreatable: Boolean,
    val everyoneCanView: Boolean,
    /** Roles that may see the channel; empty if [everyoneCanView]. */
    val viewerRoleIds: Set<String>,
) {
    /** Whether a member with [roleIds] may see the channel; overwrites for single members are not considered. */
    fun visibleWith(roleIds: Collection<String>) = everyoneCanView || roleIds.any { it in viewerRoleIds }
}
