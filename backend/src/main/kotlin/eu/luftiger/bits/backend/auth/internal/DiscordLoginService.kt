package eu.luftiger.bits.backend.auth.internal

import eu.luftiger.bits.backend.auth.domain.Role
import eu.luftiger.bits.backend.auth.domain.SessionUser
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
 * members with the Administrator permission become [Role.ADMIN]. The access token is used for these two calls only
 * and then dropped.
 */
@Service
class DiscordLoginService(
    private val discordApi: DiscordApi,
    private val properties: AuthProperties,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val accessToken = userRequest.accessToken.tokenValue
        try {
            val guild =
                discordApi.getCurrentUserGuilds(accessToken).firstOrNull { it.id == properties.discord.guildId }
                    ?: throw OAuth2AuthenticationException(OAuth2Error(NOT_MEMBER_ERROR))
            val user = discordApi.getCurrentUser(accessToken)
            val isAdmin = guild.owner || BigInteger(guild.permissions).testBit(ADMINISTRATOR_BIT)
            return DiscordPrincipal(
                SessionUser.discord(
                    discordUserId = user.id,
                    name = user.globalName ?: user.username,
                    avatarUrl = avatarUrl(user),
                    role = if (isAdmin) Role.ADMIN else Role.USER,
                ),
            )
        } catch (e: RestClientException) {
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
