package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.PingRequest
import eu.luftiger.bits.shared.PingResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/** [BackendClient] that authenticates with the service token of the bot. */
class KtorBackendClient(
    baseUrl: String,
    serviceToken: String,
    engine: HttpClientEngine = CIO.create(),
) : BackendClient,
    AutoCloseable {
    private val http =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(BitsJson) }
            defaultRequest {
                url(baseUrl.trimEnd('/') + "/")
                bearerAuth(serviceToken)
            }
        }

    override suspend fun ping(discordUserId: String): PingResponse =
        http
            .post("api/ping") {
                contentType(ContentType.Application.Json)
                setBody(PingRequest(discordUserId))
            }.body()

    override fun close() = http.close()
}
