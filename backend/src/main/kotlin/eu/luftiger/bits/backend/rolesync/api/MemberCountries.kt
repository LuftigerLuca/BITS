package eu.luftiger.bits.backend.rolesync.api

import org.springframework.modulith.NamedInterface

/**
 * Cross-module boundary of the rolesync module: the citizenship of the linked users as the role sync loaded it last,
 * so other modules need no WarEra calls of their own for it. Kept in memory only, so it is empty after a start until
 * the first hourly sync, and stays so while the role sync and nicknames are off.
 */
@NamedInterface("api")
interface MemberCountries {
    /** Country ID by WarEra user ID; users without a country or who could not be loaded are missing. */
    fun byWareraUserId(): Map<String, String>
}
