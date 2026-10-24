package eu.luftiger.bits.backend.auth.domain

/** Decided once at login and kept in the session token until it expires. */
enum class Role {
    USER,
    ADMIN,
}
