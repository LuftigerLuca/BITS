package eu.luftiger.bits.backend.rolesync.api

import eu.luftiger.bits.backend.rolesync.api.dto.MemberRoleExplanation
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the role sync: which managed roles a member meets and why, as `/rollen` tells them. */
@NamedInterface("api")
interface RoleExplanations {
    /**
     * `null` if [discordUserId] is not linked.
     *
     * @throws eu.luftiger.bits.warera.WareraApiException if the user cannot be loaded
     */
    fun explain(discordUserId: String): MemberRoleExplanation?
}
