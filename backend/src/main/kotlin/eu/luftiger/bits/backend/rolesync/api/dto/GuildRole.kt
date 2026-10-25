package eu.luftiger.bits.backend.rolesync.api.dto

import org.springframework.modulith.NamedInterface

@NamedInterface("api")
data class GuildRole(
    val id: String,
    val name: String,
    /** RGB value, `null` for the default color. */
    val color: Int?,
)
