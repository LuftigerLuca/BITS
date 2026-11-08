package eu.luftiger.bits.backend.auth.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.net.URLEncoder

class RestDiscordApi(
    private val restClient: RestClient,
    private val clientId: String,
    private val clientSecret: String,
) : DiscordApi {
    override fun refreshTokens(refreshToken: String): DiscordTokens {
        val form = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refreshToken, Charsets.UTF_8)}"
        val body =
            restClient
                .post()
                .uri("/oauth2/token")
                .headers { it.setBasicAuth(clientId, clientSecret, Charsets.UTF_8) }
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                // Discord answers `invalid_grant` with 400 for a refresh token it no longer accepts.
                .onStatus({ it.isSameCodeAs(HttpStatus.BAD_REQUEST) }) { _, response ->
                    throw DiscordGrantRevokedException("Discord rejected the refresh token (${response.statusCode})")
                }.body(String::class.java)
                ?: error("Empty response from Discord for /oauth2/token")
        return json.decodeFromString(DiscordTokens.serializer(), body)
    }

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
                .onStatus({ it.isSameCodeAs(HttpStatus.UNAUTHORIZED) }) { _, _ ->
                    throw DiscordGrantRevokedException("Discord rejected the access token for $path")
                }.body(String::class.java)
                ?: error("Empty response from Discord for $path")
        return json.decodeFromString(serializer, body)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
