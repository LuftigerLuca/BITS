package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.bot.status.BackendCallCounter
import eu.luftiger.bits.shared.BackendCallCount
import eu.luftiger.bits.shared.BotActivity
import eu.luftiger.bits.shared.BotActivityType
import eu.luftiger.bits.shared.BotHeartbeat
import eu.luftiger.bits.shared.BotPresence
import eu.luftiger.bits.shared.BotStatus
import eu.luftiger.bits.shared.CastVoteResponse
import eu.luftiger.bits.shared.DiscordRequestCount
import eu.luftiger.bits.shared.DiscordRoleDto
import eu.luftiger.bits.shared.FeatureStates
import eu.luftiger.bits.shared.NicknamePreferenceResponse
import eu.luftiger.bits.shared.OpenVote
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.shared.ProfileResponse
import eu.luftiger.bits.shared.ReportedMember
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.UnlinkAccountResponse
import eu.luftiger.bits.shared.VoteChoice
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

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
    fun `features are read from the backend and missing ones count as on`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("http://backend:8080/api/features", request.url.toString())
                    respond("""{"votes":false}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(FeatureStates(votes = false), client.features())
        }

    @Test
    fun `the presence is read from the backend`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("http://backend:8080/api/bot-presence", request.url.toString())
                    respond(
                        """{"status":"IDLE","activity":{"type":"WATCHING","text":"WarEra"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(BotPresence(BotStatus.IDLE, BotActivity(BotActivityType.WATCHING, "WarEra")), client.botPresence())
        }

    @Test
    fun `a click posts the choice of the member to the vote`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("http://backend:8080/api/votes/7/ballots", request.url.toString())
                    assertEquals("""{"discordUserId":"42","choice":"ABSTAIN"}""", request.body.toByteArray().decodeToString())
                    respond("""{"type":"cast","previous":"YES"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(CastVoteResponse.Cast(VoteChoice.YES), client.castVote(7, "42", VoteChoice.ABSTAIN))
        }

    @Test
    fun `open votes come as a list`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("http://backend:8080/api/votes/open", request.url.toString())
                    respond(
                        """{"votes":[{"id":7,"question":"Steuern?"}]}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(listOf(OpenVote(7, "Steuern?")), client.openVotes())
        }

    @Test
    fun `error responses fail the call`() =
        runTest {
            val client = KtorBackendClient("http://backend:8080", "wrong", MockEngine { respond("", HttpStatusCode.Unauthorized) })

            assertFails { client.ping("42") }
        }

    @Test
    fun `unlink posts the user and reads the former link`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("http://backend:8080/api/verifications/unlink", request.url.toString())
                    assertEquals("""{"discordUserId":"42"}""", request.body.toByteArray().decodeToString())
                    respond("""{"wareraUsername":"Luftiger"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(UnlinkAccountResponse(wareraUsername = "Luftiger"), client.unlinkAccount("42"))
        }

    @Test
    fun `nickname preference posts the user and the choice`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("http://backend:8080/api/nickname-preferences", request.url.toString())
                    assertEquals("""{"discordUserId":"42","enabled":true}""", request.body.toByteArray().decodeToString())
                    respond(
                        """{"enabled":true,"wareraUsername":"Luftiger"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(NicknamePreferenceResponse(true, "Luftiger"), client.setNicknamePreference("42", true))
        }

    @Test
    fun `profile posts the user and decodes the outcome by its type`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("http://backend:8080/api/profiles", request.url.toString())
                    assertEquals("""{"discordUserId":"42"}""", request.body.toByteArray().decodeToString())
                    respond("""{"type":"not_linked"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(ProfileResponse.NotLinked, client.profile("42"))
        }

    @Test
    fun `role explanation posts the user and decodes the outcome by its type`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("http://backend:8080/api/role-explanations", request.url.toString())
                    assertEquals("""{"discordUserId":"42"}""", request.body.toByteArray().decodeToString())
                    respond("""{"type":"not_linked"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(RoleExplanationResponse.NotLinked, client.explainRoles("42"))
        }

    @Test
    fun `verification responses are decoded by their type`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("http://backend:8080/api/verifications", request.url.toString())
                    assertEquals("""{"discordUserId":"42","wareraUsername":"Luftiger"}""", request.body.toByteArray().decodeToString())
                    respond(
                        """{"type":"started","code":"BITS-ABC234","wareraUsername":"Luftiger","expiresAt":5,"linkedWareraUsername":null}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            assertEquals(
                StartVerificationResponse.Started(
                    code = "BITS-ABC234",
                    wareraUsername = "Luftiger",
                    expiresAt = 5,
                    linkedWareraUsername = null,
                ),
                client.startVerification("42", "Luftiger"),
            )
        }

    @Test
    fun `unreachable WarEra is reported as its own exception`() =
        runTest {
            val client = KtorBackendClient("http://backend:8080", "secret", MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })

            assertFailsWith<WareraUnavailableException> { client.checkVerification("42", "BITS-ABC234") }
        }

    @Test
    fun `other server errors are not mistaken for WarEra`() =
        runTest {
            val client = KtorBackendClient("http://backend:8080", "secret", MockEngine { respond("", HttpStatusCode.InternalServerError) })

            assertFalse(assertFails { client.checkVerification("42", "BITS-ABC234") } is WareraUnavailableException)
        }

    @Test
    fun `roles are reported with PUT`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("http://backend:8080/api/discord-roles", request.url.toString())
                    assertEquals(
                        """{"roles":[{"id":"1","name":"Veteran","color":null,"position":2,"assignable":true}]}""",
                        request.body.toByteArray().decodeToString(),
                    )
                    respond("", HttpStatusCode.NoContent)
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            client.reportRoles(listOf(DiscordRoleDto(id = "1", name = "Veteran", color = null, position = 2, assignable = true)))
        }

    @Test
    fun `members are reported with PUT, joins and departures with POST`() =
        runTest {
            val requests = mutableListOf<Triple<HttpMethod, String, String>>()
            val engine =
                MockEngine { request ->
                    requests += Triple(request.method, request.url.encodedPath, request.body.toByteArray().decodeToString())
                    respond("", HttpStatusCode.NoContent)
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine)

            client.reportMembers(listOf(ReportedMember("1", "Luca", listOf("r1")), ReportedMember("2", "Mia", emptyList())))
            client.reportMemberJoined("3")
            client.reportMemberLeft("1", "Luca")

            assertEquals(
                listOf(
                    Triple(
                        HttpMethod.Put,
                        "/api/discord-members",
                        """{"discordUserIds":["1","2"],"members":[{"discordUserId":"1","name":"Luca","roleIds":["r1"]},""" +
                            """{"discordUserId":"2","name":"Mia","roleIds":[]}]}""",
                    ),
                    Triple(HttpMethod.Post, "/api/discord-members/joined", """{"discordUserId":"3"}"""),
                    Triple(HttpMethod.Post, "/api/discord-members/left", """{"discordUserId":"1","memberName":"Luca"}"""),
                ),
                requests,
            )
        }

    @Test
    fun `a backend that does not answer in time is reported as its own exception`() =
        runTest {
            val engine =
                MockEngine {
                    delay(5_000)
                    respond("")
                }
            val client = KtorBackendClient("http://backend:8080", "secret", engine, requestTimeout = Duration.ofMillis(100))

            assertFailsWith<BackendTimeoutException> { client.startVerification("42", "Luftiger") }
        }

    @Test
    fun `every request is counted by endpoint with how it ended`() =
        runTest {
            val engine =
                MockEngine { request ->
                    when (request.url.encodedPath) {
                        "/api/votes/7/result/posted" -> respond("")
                        "/api/votes/8/result/posted" -> respond("", HttpStatusCode.InternalServerError)
                        "/api/features" -> throw java.io.IOException("connection refused")
                        else -> {
                            delay(5_000)
                            respond("")
                        }
                    }
                }
            val calls = BackendCallCounter()
            val client = KtorBackendClient("http://backend:8080", "secret", engine, requestTimeout = Duration.ofMillis(100), calls = calls)

            client.markVoteResultPosted(7)
            assertFails { client.markVoteResultPosted(8) }
            assertFails { client.features() }
            assertFails {
                client.heartbeat(
                    BotHeartbeat("dev", 0, "CONNECTED", 0, null, 0, 0, 0, emptyList(), DiscordRequestCount(0, 0, 0, 0)),
                )
            }

            assertEquals(
                setOf(
                    BackendCallCount("POST /api/votes/{id}/result/posted", succeeded = 1, failed = 1, timedOut = 0, unreachable = 0),
                    BackendCallCount("GET /api/features", succeeded = 0, failed = 0, timedOut = 0, unreachable = 1),
                    BackendCallCount("POST /api/bot-heartbeat", succeeded = 0, failed = 0, timedOut = 1, unreachable = 0),
                ),
                calls.snapshot().toSet(),
            )
        }
}
