package eu.luftiger.bits.backend.auth.domain

/**
 * Who a session token was issued to. The subject is `discord:<id>` for Discord users and `admin:<username>` for the
 * admin account from the environment, so the two can never collide.
 */
data class SessionUser(
    val subject: String,
    val name: String,
    val avatarUrl: String?,
    val role: Role,
    /** The member's roles in the guild at login; empty for the admin account. */
    val discordRoleIds: List<String> = emptyList(),
) {
    val discordUserId: String?
        get() = subject.takeIf { it.startsWith(DISCORD_PREFIX) }?.removePrefix(DISCORD_PREFIX)

    companion object {
        private const val DISCORD_PREFIX = "discord:"
        private const val ADMIN_PREFIX = "admin:"

        fun discord(
            discordUserId: String,
            name: String,
            avatarUrl: String,
            role: Role,
            discordRoleIds: List<String> = emptyList(),
        ) = SessionUser(DISCORD_PREFIX + discordUserId, name, avatarUrl, role, discordRoleIds)

        fun admin(username: String) = SessionUser(ADMIN_PREFIX + username, username, avatarUrl = null, role = Role.ADMIN)
    }
}
