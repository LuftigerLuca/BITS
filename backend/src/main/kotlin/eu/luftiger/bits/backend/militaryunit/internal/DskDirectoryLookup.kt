package eu.luftiger.bits.backend.militaryunit.internal

import eu.luftiger.bits.backend.militaryunit.api.DskDirectory
import eu.luftiger.bits.backend.militaryunit.api.dto.DskRecord
import eu.luftiger.bits.backend.militaryunit.api.dto.DskUnitRecord
import org.springframework.stereotype.Component

@Component
class DskDirectoryLookup(
    private val dskRepository: DskRepository,
    private val militaryUnitRepository: DskMilitaryUnitRepository,
) : DskDirectory {
    override fun all(): List<DskRecord> {
        val units = militaryUnitRepository.findAll().groupBy { it.dskId }
        return dskRepository.findAll().sortedBy { it.name.lowercase() }.map { dsk ->
            val id = requireNotNull(dsk.id)
            DskRecord(
                id = id,
                name = dsk.name,
                roleId = dsk.roleId,
                militaryUnits =
                    units[id]
                        .orEmpty()
                        .sortedBy { it.muName.lowercase() }
                        .map { DskUnitRecord(it.muId, it.muName, it.assignedAt) },
            )
        }
    }
}
