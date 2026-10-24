package eu.luftiger.bits.backend.auth.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The few Discord endpoints the login needs, called with the access token of the user who logs in. Everything else
 * that talks to Discord is the bot's job.
 */
interface DiscordApi {
    fun getCurrentUser(accessToken: String): DiscordUser

    fun getCurrentUserGuilds(accessToken: String): List<DiscordGuild>
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
data class DiscordGuild(
    val id: String,
    val owner: Boolean = false,
    /** Permission bit set of the user in this guild as a decimal string, since it does not fit into 53 bits. */
    val permissions: String = "0",
)
