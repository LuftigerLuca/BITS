package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.BotPresence
import eu.luftiger.bits.shared.CastVoteRequest
import eu.luftiger.bits.shared.CastVoteResponse
import eu.luftiger.bits.shared.CheckVerificationRequest
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.CloseVoteRequest
import eu.luftiger.bits.shared.CloseVoteResponse
import eu.luftiger.bits.shared.DiscordChannelDto
import eu.luftiger.bits.shared.DiscordChannelsReport
import eu.luftiger.bits.shared.DiscordMemberChange
import eu.luftiger.bits.shared.DiscordMembersReport
import eu.luftiger.bits.shared.DiscordRoleDto
import eu.luftiger.bits.shared.DiscordRolesReport
import eu.luftiger.bits.shared.DiscordThreadDto
import eu.luftiger.bits.shared.DiscordThreadsReport
import eu.luftiger.bits.shared.Elector
import eu.luftiger.bits.shared.ElectorateReport
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadCreationReport
import eu.luftiger.bits.shared.FeatureStates
import eu.luftiger.bits.shared.NicknamePreferenceRequest
import eu.luftiger.bits.shared.NicknamePreferenceResponse
import eu.luftiger.bits.shared.OpenVote
import eu.luftiger.bits.shared.OpenVotesResponse
import eu.luftiger.bits.shared.ParticipationRequest
import eu.luftiger.bits.shared.ParticipationResponse
import eu.luftiger.bits.shared.PingRequest
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.shared.ProfileRequest
import eu.luftiger.bits.shared.ProfileResponse
import eu.luftiger.bits.shared.ReportedMember
import eu.luftiger.bits.shared.RoleExplanationRequest
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.shared.RoleSyncReport
import eu.luftiger.bits.shared.StartVerificationRequest
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.StartVoteRequest
import eu.luftiger.bits.shared.StartVoteResponse
import eu.luftiger.bits.shared.UndeliveredEventReport
import eu.luftiger.bits.shared.UnlinkAccountRequest
import eu.luftiger.bits.shared.UnlinkAccountResponse
import eu.luftiger.bits.shared.VoteActor
import eu.luftiger.bits.shared.VoteChoice
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteMessageReport
import eu.luftiger.bits.shared.VoteResultDto
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
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.time.Duration

/** [BackendClient], [VoteBackend], [EmbassyBackend], [FeatureBackend] and [PresenceBackend] that authenticate with the
 * service token of the bot.
 */
class KtorBackendClient(
    baseUrl: String,
    serviceToken: String,
    engine: HttpClientEngine = CIO.create(),
    requestTimeout: Duration = REQUEST_TIMEOUT,
) : BackendClient,
    VoteBackend,
    EmbassyBackend,
    FeatureBackend,
    PresenceBackend,
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
        discordName: String?,
    ): CheckVerificationResponse = post("api/verifications/check", CheckVerificationRequest(discordUserId, code, discordName))

    override suspend fun unlinkAccount(discordUserId: String): UnlinkAccountResponse =
        post("api/verifications/unlink", UnlinkAccountRequest(discordUserId))

    override suspend fun setNicknamePreference(
        discordUserId: String,
        enabled: Boolean,
    ): NicknamePreferenceResponse = post("api/nickname-preferences", NicknamePreferenceRequest(discordUserId, enabled))

    override suspend fun profile(discordUserId: String): ProfileResponse = post("api/profiles", ProfileRequest(discordUserId))

    override suspend fun explainRoles(discordUserId: String): RoleExplanationResponse =
        post("api/role-explanations", RoleExplanationRequest(discordUserId))

    override suspend fun reportRoles(roles: List<DiscordRoleDto>) {
        http.put("api/discord-roles") {
            contentType(ContentType.Application.Json)
            setBody(DiscordRolesReport(roles))
        }
    }

    override suspend fun reportChannels(channels: List<DiscordChannelDto>) {
        http.put("api/discord-channels") {
            contentType(ContentType.Application.Json)
            setBody(DiscordChannelsReport(channels))
        }
    }

    override suspend fun reportMembers(members: List<ReportedMember>) {
        http.put("api/discord-members") {
            contentType(ContentType.Application.Json)
            setBody(DiscordMembersReport(members.map { it.discordUserId }, members))
        }
    }

    override suspend fun reportMemberJoined(discordUserId: String) {
        http.post("api/discord-members/joined") {
            contentType(ContentType.Application.Json)
            setBody(DiscordMemberChange(discordUserId))
        }
    }

    override suspend fun reportMemberLeft(
        discordUserId: String,
        memberName: String,
    ) {
        http.post("api/discord-members/left") {
            contentType(ContentType.Application.Json)
            setBody(DiscordMemberChange(discordUserId, memberName))
        }
    }

    override suspend fun reportRoleSync(
        syncId: Long,
        report: RoleSyncReport,
    ) {
        http.post("api/system-log/role-syncs/$syncId") {
            contentType(ContentType.Application.Json)
            setBody(report)
        }
    }

    override suspend fun reportUndeliveredEvent(report: UndeliveredEventReport) {
        http.post("api/system-log/undelivered-events") {
            contentType(ContentType.Application.Json)
            setBody(report)
        }
    }

    override suspend fun reportThreads(threads: List<DiscordThreadDto>) {
        http.put("api/discord-threads") {
            contentType(ContentType.Application.Json)
            setBody(DiscordThreadsReport(threads))
        }
    }

    override suspend fun reportEmbassySync(
        syncId: Long,
        report: EmbassySyncReport,
    ) {
        http.post("api/embassies/syncs/$syncId") {
            contentType(ContentType.Application.Json)
            setBody(report)
        }
    }

    override suspend fun reportThreadCreation(
        creationId: Long,
        report: EmbassyThreadCreationReport,
    ) {
        http.post("api/embassies/thread-creations/$creationId") {
            contentType(ContentType.Application.Json)
            setBody(report)
        }
    }

    override suspend fun features(): FeatureStates = http.get("api/features").body()

    override suspend fun botPresence(): BotPresence = http.get("api/bot-presence").body()

    override suspend fun startVote(request: StartVoteRequest): StartVoteResponse = post("api/votes", request)

    override suspend fun openVotes(): List<OpenVote> = http.get("api/votes/open").body<OpenVotesResponse>().votes

    override suspend fun vote(voteId: Long): VoteDto = http.get("api/votes/$voteId").body()

    override suspend fun closeVote(
        voteId: Long,
        actor: VoteActor,
        cancel: Boolean,
    ): CloseVoteResponse = post("api/votes/$voteId/close", CloseVoteRequest(actor, cancel))

    override suspend fun castVote(
        voteId: Long,
        discordUserId: String,
        choice: VoteChoice,
    ): CastVoteResponse = post("api/votes/$voteId/ballots", CastVoteRequest(discordUserId, choice))

    override suspend fun participation(
        voteId: Long,
        roleMemberIds: List<String>,
    ): ParticipationResponse = post("api/votes/$voteId/participation", ParticipationRequest(roleMemberIds))

    override suspend fun reportVoteMessage(
        voteId: Long,
        messageId: String,
    ) {
        http.put("api/votes/$voteId/message") {
            contentType(ContentType.Application.Json)
            setBody(VoteMessageReport(messageId))
        }
    }

    override suspend fun reportElectorate(
        voteId: Long,
        electors: List<Elector>,
    ) {
        http.put("api/votes/$voteId/electorate") {
            contentType(ContentType.Application.Json)
            setBody(ElectorateReport(electors))
        }
    }

    override suspend fun voteResult(voteId: Long): VoteResultDto = http.get("api/votes/$voteId/result").body()

    override suspend fun markVoteResultPosted(voteId: Long) {
        http.post("api/votes/$voteId/result/posted")
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
