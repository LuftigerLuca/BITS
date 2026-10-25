package eu.luftiger.bits.backend.overview.api

import eu.luftiger.bits.backend.overview.api.dto.SelectedCountry
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the overview module: the WarEra country an admin picked under "Verwaltung". */
@NamedInterface("api")
interface OverviewCountry {
    /** `null` while no country is set. */
    fun selected(): SelectedCountry?
}
