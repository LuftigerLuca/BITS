package eu.luftiger.bits.backend.militaryunit.internal

import eu.luftiger.bits.backend.rolesync.api.DskRoles
import eu.luftiger.bits.backend.rolesync.api.dto.DskRole
import org.springframework.stereotype.Component

/** Hands the DSKs to the role sync; with the repositories only, since the role sync needs it to start. */
@Component
class DskRoleLookup(
    private val dskRepository: DskRepository,
    private val militaryUnitRepository: DskMilitaryUnitRepository,
) : DskRoles {
    override fun all(): List<DskRole> {
        val units = militaryUnitRepository.findAll().groupBy { it.dskId }
        return dskRepository.findAll().mapNotNull { dsk ->
            val roleId = dsk.roleId ?: return@mapNotNull null
            DskRole(roleId, dsk.name, units[dsk.id].orEmpty().associate { it.muId to it.muName })
        }
    }
}
