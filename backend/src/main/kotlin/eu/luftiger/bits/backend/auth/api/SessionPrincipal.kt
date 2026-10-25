package eu.luftiger.bits.backend.auth.api

import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.internal.SessionTokens
import org.springframework.modulith.NamedInterface
import org.springframework.security.oauth2.jwt.Jwt

/** Who a dashboard request comes from, read from the session token; e.g. to record who made a change. */
@NamedInterface("api")
data class SessionPrincipal(
    /** `discord:<id>` or `admin:<username>`. */
    val subject: String,
    val name: String,
    val avatarUrl: String?,
    val admin: Boolean = false,
    /** The member's roles in the guild at login; empty for the admin account. */
    val discordRoleIds: List<String> = emptyList(),
) {
    // The companion is a class of its own, which has to be exposed as well.
    @NamedInterface("api")
    companion object {
        fun of(session: Jwt): SessionPrincipal =
            SessionTokens.user(session).let {
                SessionPrincipal(it.subject, it.name, it.avatarUrl, admin = it.role == Role.ADMIN, discordRoleIds = it.discordRoleIds)
            }
    }
}
