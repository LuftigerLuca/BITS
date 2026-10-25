package eu.luftiger.bits.backend.auth.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The few Discord endpoints the login needs, called with the access token of the user who logs in. Everything else
 * that talks to Discord is the bot's job.
 */
interface DiscordApi {
    fun getCurrentUser(accessToken: String): DiscordUser

    /** The guilds of the user, one page of up to [limit] after the guild ID [after], sorted by ID. */
    fun getCurrentUserGuilds(
        accessToken: String,
        after: String? = null,
        limit: Int = MAX_GUILDS_PER_PAGE,
    ): List<DiscordGuild>

    /** The user's membership in [guildId]; needs the scope `guilds.members.read`. */
    fun getCurrentUserGuildMember(
        accessToken: String,
        guildId: String,
    ): DiscordGuildMember

    /** Pages through the guilds of the user until [guildId] turns up, since Discord returns at most 200 at a time. */
    fun findCurrentUserGuild(
        accessToken: String,
        guildId: String,
    ): DiscordGuild? {
        var after: String? = null
        while (true) {
            val page = getCurrentUserGuilds(accessToken, after)
            page.firstOrNull { it.id == guildId }?.let { return it }
            if (page.size < MAX_GUILDS_PER_PAGE) return null
            after = page.last().id
        }
    }

    companion object {
        const val MAX_GUILDS_PER_PAGE = 200
    }
}

@Serializable
data class DiscordUser(
    val id: String,
    val username: String,
    @SerialName("global_name")
    val globalName: String? = null,
    /** Hash of the avatar, `null` for the default avatar. */
    val avatar: String? = null,
)

@Serializable
data class DiscordGuildMember(
    /** IDs of the member's roles, without `@everyone`. */
    val roles: List<String> = emptyList(),
)

@Serializable
data class DiscordGuild(
    val id: String,
    val owner: Boolean = false,
    /** Permission bit set of the user in this guild as a decimal string, since it does not fit into 53 bits. */
    val permissions: String = "0",
)
