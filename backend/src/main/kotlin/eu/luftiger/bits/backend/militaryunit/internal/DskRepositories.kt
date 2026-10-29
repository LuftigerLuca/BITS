package eu.luftiger.bits.backend.militaryunit.internal

import eu.luftiger.bits.backend.militaryunit.domain.Dsk
import eu.luftiger.bits.backend.militaryunit.domain.DskMilitaryUnit
import org.springframework.data.jpa.repository.JpaRepository

interface DskRepository : JpaRepository<Dsk, Long> {
    fun findByRoleId(roleId: String): Dsk?

    fun existsByNameIgnoreCase(name: String): Boolean
}

interface DskMilitaryUnitRepository : JpaRepository<DskMilitaryUnit, String> {
    fun findByDskId(dskId: Long): List<DskMilitaryUnit>
}
