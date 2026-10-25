package eu.luftiger.bits.backend.auth.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

class RestDiscordApi(
    private val restClient: RestClient,
) : DiscordApi {
    override fun getCurrentUser(accessToken: String): DiscordUser = get("/users/@me", accessToken, DiscordUser.serializer())

    override fun getCurrentUserGuilds(
        accessToken: String,
        after: String?,
        limit: Int,
    ): List<DiscordGuild> {
        val query = "?limit=$limit" + after?.let { "&after=$it" }.orEmpty()
        return get("/users/@me/guilds$query", accessToken, ListSerializer(DiscordGuild.serializer()))
    }

    override fun getCurrentUserGuildMember(
        accessToken: String,
        guildId: String,
    ): DiscordGuildMember = get("/users/@me/guilds/$guildId/member", accessToken, DiscordGuildMember.serializer())

    private fun <T> get(
        path: String,
        accessToken: String,
        serializer: KSerializer<T>,
    ): T {
        val body =
            restClient
                .get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .body(String::class.java)
                ?: error("Empty response from Discord for $path")
        return json.decodeFromString(serializer, body)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
