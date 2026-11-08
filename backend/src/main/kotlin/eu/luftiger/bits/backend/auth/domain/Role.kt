package eu.luftiger.bits.backend.auth.domain

/** Decided at login and again whenever the session's token is renewed, see `SessionRefresher`. */
enum class Role {
    USER,
    ADMIN,
}
