package eu.luftiger.bits.backend.feature.api

import kotlinx.serialization.Serializable
import org.springframework.modulith.NamedInterface

/**
 * A part of BITS that admins can switch off under "Verwaltung". Stored by name and part of the dashboard API, so
 * existing names stay as they are; the dashboard spells them out in `frontend/src/lib/api.ts`.
 */
@NamedInterface("api")
@Serializable
enum class BitsFeature {
    /** `/verify` and the button that checks the code. Unlinking always works, since it is about the member's data. */
    VERIFICATION,

    /** The sync of the managed roles and `/rollen`. */
    ROLE_SYNC,

    /** Nicknames from WarEra, set by the same sync as the roles, and `/nickname`. */
    NICKNAMES,

    /** `/profil`. */
    PROFILE,

    /** Starting votes; votes that are open already run to their end. */
    VOTES,

    /** The sync of the embassy threads. */
    EMBASSIES,
}
