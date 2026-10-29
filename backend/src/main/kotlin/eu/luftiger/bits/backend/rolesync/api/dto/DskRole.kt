package eu.luftiger.bits.backend.rolesync.api.dto

import org.springframework.modulith.NamedInterface

/**
 * A DSK, a group of military units whose linked members get [roleId]; everybody else loses it. A role belongs to one
 * DSK at most and then has no rule.
 */
@NamedInterface("api")
data class DskRole(
    val roleId: String,
    /** E.g. "DSK1". */
    val name: String,
    /** Names of the military units by ID, as of the time they were assigned; may be empty. */
    val militaryUnits: Map<String, String>,
)
