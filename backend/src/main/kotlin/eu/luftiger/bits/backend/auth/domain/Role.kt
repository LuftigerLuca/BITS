package eu.luftiger.bits.backend.auth.domain

import kotlinx.serialization.Serializable

/** Decided once at login and kept in the session token until it expires. */
@Serializable
enum class Role {
    USER,
    ADMIN,
}
