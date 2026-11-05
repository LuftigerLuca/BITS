package eu.luftiger.bits.backend.militaryunit.api

import eu.luftiger.bits.backend.militaryunit.api.dto.DskRecord
import org.springframework.modulith.NamedInterface

/** Read access to the DSKs and their military units for the third-party API. */
@NamedInterface("api")
interface DskDirectory {
    /** By name. */
    fun all(): List<DskRecord>
}
