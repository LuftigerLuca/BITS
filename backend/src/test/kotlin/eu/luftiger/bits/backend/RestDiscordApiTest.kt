package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.auth.internal.RestDiscordApi
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals

class RestDiscordApiTest {
    private val builder = RestClient.builder().baseUrl("https://discord.test/api")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val api = RestDiscordApi(builder.build())

    @Test
    fun `guilds are paged until the guild turns up`() {
        val firstPage = (1..200).joinToString(",", "[", "]") { """{"id":"$it"}""" }
        server
            .expect(requestTo("https://discord.test/api/users/@me/guilds?limit=200"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andRespond(withSuccess(firstPage, MediaType.APPLICATION_JSON))
        server
            .expect(requestTo("https://discord.test/api/users/@me/guilds?limit=200&after=200"))
            .andRespond(withSuccess("""[{"id":"201"},{"id":"300","owner":true}]""", MediaType.APPLICATION_JSON))

        assertEquals(true, api.findCurrentUserGuild("token", "300")?.owner)
        server.verify()
    }

    @Test
    fun `a short page is the last one`() {
        server
            .expect(requestTo("https://discord.test/api/users/@me/guilds?limit=200"))
            .andRespond(withSuccess("""[{"id":"1"}]""", MediaType.APPLICATION_JSON))

        assertEquals(null, api.findCurrentUserGuild("token", "300"))
        server.verify()
    }
}
