package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.PingResponse
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class KtorBackendClientTest {
    @Test
    fun `ping posts the user with the service token`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("http://backend:8080/api/ping", request.url.toString())
                    assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
                    assertEquals("""{"discordUserId":"42"}""", request.body.toByteArray().decodeToString())
                    respond(
                        """{"pingCount":3,"wareraCountryCount":null}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(PingResponse(pingCount = 3, wareraCountryCount = null), client.ping("42"))
        }

    @Test
    fun `error responses fail the call`() =
        runTest {
            val client = KtorBackendClient("http://backend:8080", "wrong", MockEngine { respond("", HttpStatusCode.Unauthorized) })

            assertFails { client.ping("42") }
        }
}
