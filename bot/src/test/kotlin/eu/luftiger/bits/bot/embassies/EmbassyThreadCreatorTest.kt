package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.bot.backend.KtorBackendClient
import eu.luftiger.bits.shared.BitsJson
import eu.luftiger.bits.shared.CreatedThread
import eu.luftiger.bits.shared.EmbassyThreadCreationEvent
import eu.luftiger.bits.shared.EmbassyThreadCreationReport
import eu.luftiger.bits.shared.ThreadCreationFailure
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbassyThreadCreatorTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val factory = FakeFactory()
    private val reports = mutableListOf<Pair<String, EmbassyThreadCreationReport>>()
    private var answer = HttpStatusCode.NoContent

    private val backend =
        KtorBackendClient(
            "http://backend:8080",
            "secret",
            MockEngine { request ->
                val body = BitsJson.decodeFromString(EmbassyThreadCreationReport.serializer(), request.body.toByteArray().decodeToString())
                reports += request.url.encodedPath to body
                respond("", answer)
            },
        )
    private val creator = EmbassyThreadCreator(factory, backend, clock, retryDelayMillis = 0)

    @Test
    fun `creates a private thread and reports it`() =
        runTest {
            creator.handle(event())

            assertEquals(listOf("c-1 Botschaft Polen (BITS: Botschaft von Polen)"), factory.created)
            assertEquals(
                listOf(
                    "/api/embassies/thread-creations/7" to
                        EmbassyThreadCreationReport(thread = CreatedThread("t-1", "Botschaft Polen", "c-1", "botschaften")),
                ),
                reports,
            )
        }

    @Test
    fun `the same event again creates no second thread`() =
        runTest {
            creator.handle(event())
            creator.handle(event())

            assertEquals(1, factory.created.size)
        }

    @Test
    fun `reports why it could not create the thread`() =
        runTest {
            factory.result = ThreadCreation.NoPermission

            creator.handle(event())

            assertEquals(listOf(EmbassyThreadCreationReport(failure = ThreadCreationFailure.NO_PERMISSION)), reports.map { it.second })
        }

    @Test
    fun `a thread the backend no longer wants is deleted`() =
        runTest {
            answer = HttpStatusCode.Gone

            creator.handle(event())

            assertEquals(listOf("t-1"), factory.deleted)
        }

    @Test
    fun `an event older than a minute is dropped, since the backend gave up on it`() =
        runTest {
            creator.handle(event(occurredAt = now.minusSeconds(61)))

            assertEquals(emptyList(), factory.created)
            assertEquals(emptyList(), reports)
        }

    private fun event(occurredAt: Instant = now.minusSeconds(1)) =
        EmbassyThreadCreationEvent(7, "c-1", "Botschaft Polen", "Polen", occurredAt.toEpochMilli())

    private class FakeFactory : EmbassyThreadFactory {
        val created = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var result: ThreadCreation? = null

        override suspend fun createPrivateThread(
            channelId: String,
            name: String,
            reason: String,
        ): ThreadCreation {
            result?.let { return it }
            created += "$channelId $name ($reason)"
            return ThreadCreation.Created(CreatedThread("t-${created.size}", name, channelId, "botschaften"))
        }

        override suspend fun deleteThread(
            threadId: String,
            reason: String,
        ) {
            deleted += threadId
        }
    }
}
