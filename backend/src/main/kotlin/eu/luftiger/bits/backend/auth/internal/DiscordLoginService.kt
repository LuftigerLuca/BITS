package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
import org.slf4j.LoggerFactory
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.math.BigInteger

/**
 * Turns a Discord login into a [SessionUser]. Only members of the configured guild get in; the guild's owner and
 * members with the Administrator permission become [Role.ADMIN]. The member's roles go into the session as well, for
 * permissions given to roles such as managing votes. The access token is used for these calls only and then dropped.
 */
@Service
class DiscordLoginService(
    private val discordApi: DiscordApi,
    private val properties: AuthProperties,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val accessToken = userRequest.accessToken.tokenValue
        try {
            val user = discordApi.getCurrentUser(accessToken)
            val guild = discordApi.findCurrentUserGuild(accessToken, properties.discord.guildId)
            if (guild == null) {
                log.info("Discord login of {} rejected: not a member of the guild", user.id)
                throw OAuth2AuthenticationException(OAuth2Error(NOT_MEMBER_ERROR))
            }
            val member = discordApi.getCurrentUserGuildMember(accessToken, properties.discord.guildId)
            val isAdmin = guild.owner || BigInteger(guild.permissions).testBit(ADMINISTRATOR_BIT)
            val role = if (isAdmin) Role.ADMIN else Role.USER
            log.info("Discord login of {} as {}", user.id, role)
            return DiscordPrincipal(
                SessionUser.discord(
                    discordUserId = user.id,
                    name = user.globalName ?: user.username,
                    avatarUrl = avatarUrl(user),
                    role = role,
                    discordRoleIds = member.roles,
                ),
            )
        } catch (e: RestClientException) {
            // The message names the URL and status, never the access token, which only travels in a header.
            log.warn("Discord login failed: Discord not reachable ({})", e.message)
            throw OAuth2AuthenticationException(OAuth2Error(DISCORD_UNAVAILABLE_ERROR), e)
        }
    }

    private fun avatarUrl(user: DiscordUser): String =
        if (user.avatar != null) {
            "https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png?size=128"
        } else {
            // Users without an avatar get one of six defaults, derived from the ID.
            "https://cdn.discordapp.com/embed/avatars/${(user.id.toLong() shr 22) % 6}.png"
        }

    /** Carries the [SessionUser] from here to [DiscordLoginSuccessHandler]. */
    class DiscordPrincipal(
        val user: SessionUser,
    ) : OAuth2User {
        override fun getName(): String = user.subject

        override fun getAttributes(): Map<String, Any> = emptyMap()

        override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_${user.role}"))
    }

    companion object {
        const val NOT_MEMBER_ERROR = "not_member"
        const val DISCORD_UNAVAILABLE_ERROR = "discord_unavailable"
        private const val ADMINISTRATOR_BIT = 3
    }
}
