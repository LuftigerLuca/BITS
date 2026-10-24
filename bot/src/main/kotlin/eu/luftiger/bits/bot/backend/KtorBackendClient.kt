package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.CheckVerificationRequest
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.DiscordRoleDto
import eu.luftiger.bits.shared.DiscordRolesReport
import eu.luftiger.bits.shared.PingRequest
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.shared.StartVerificationRequest
import eu.luftiger.bits.shared.StartVerificationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.Duration

/** [BackendClient] that authenticates with the service token of the bot. */
class KtorBackendClient(
    baseUrl: String,
    serviceToken: String,
    engine: HttpClientEngine = CIO.create(),
    requestTimeout: Duration = REQUEST_TIMEOUT,
) : BackendClient,
    AutoCloseable {
    private val http =
        HttpClient(engine) {
            expectSuccess = true
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT.toMillis()
                requestTimeoutMillis = requestTimeout.toMillis()
            }
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

    override suspend fun startVerification(
        discordUserId: String,
        wareraUsername: String,
    ): StartVerificationResponse = post("api/verifications", StartVerificationRequest(discordUserId, wareraUsername))

    override suspend fun checkVerification(
        discordUserId: String,
        code: String,
    ): CheckVerificationResponse = post("api/verifications/check", CheckVerificationRequest(discordUserId, code))

    override suspend fun reportRoles(roles: List<DiscordRoleDto>) {
        http.put("api/discord-roles") {
            contentType(ContentType.Application.Json)
            setBody(DiscordRolesReport(roles))
        }
    }

    /** The backend answers 503 if WarEra cannot be reached. */
    private suspend inline fun <reified R : Any, reified T> post(
        path: String,
        request: R,
    ): T =
        try {
            http
                .post(path) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body()
        } catch (e: ServerResponseException) {
            if (e.response.status == HttpStatusCode.ServiceUnavailable) throw WareraUnavailableException(e)
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw BackendTimeoutException(e)
        }

    override fun close() = http.close()

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)

        /**
         * Starting a verification makes a few WarEra calls in the backend, each of which waits up to 10 s for the
         * rate limiter, 10 s per attempt for an answer and 6 s between its three attempts. A minute covers that
         * unless WarEra is down; checks answer at once and send their result as event.
         */
        val REQUEST_TIMEOUT: Duration = Duration.ofMinutes(1)
    }
}
