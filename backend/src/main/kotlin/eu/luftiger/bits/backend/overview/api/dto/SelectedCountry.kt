package eu.luftiger.bits.backend.overview.api.dto

import org.springframework.modulith.NamedInterface

/** The country of the dashboard; the name as of the time it was picked. */
@NamedInterface("api")
data class SelectedCountry(
    val id: String,
    val name: String,
)
