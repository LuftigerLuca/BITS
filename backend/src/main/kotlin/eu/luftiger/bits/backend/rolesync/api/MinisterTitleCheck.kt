package eu.luftiger.bits.backend.rolesync.api

import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.warera.WareraGovernment
import org.springframework.modulith.NamedInterface

/**
 * Cross-module boundary of the rolesync module: notices ministers whose title no [GovernmentOffice] stands for, so a
 * ministry WarEra adds shows up in the system log instead of silently counting for nothing.
 */
@NamedInterface("api")
interface MinisterTitleCheck {
    /** Records the unknown titles of [government] in the system log, each only once while the backend runs. */
    fun check(
        government: WareraGovernment,
        countryName: String,
    )
}
