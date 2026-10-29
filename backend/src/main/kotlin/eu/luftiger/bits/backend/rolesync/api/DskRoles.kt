package eu.luftiger.bits.backend.rolesync.api

import eu.luftiger.bits.backend.rolesync.api.dto.DskRole
import org.springframework.modulith.NamedInterface

/**
 * The DSKs with a role, which the role sync manages besides the rules. Implemented by the militaryunit module, which
 * depends on the role sync, so the role sync reads the DSKs without depending on it in turn.
 */
@NamedInterface("api")
interface DskRoles {
    /** Every DSK with a role, whether the role still exists or not. */
    fun all(): List<DskRole>
}
