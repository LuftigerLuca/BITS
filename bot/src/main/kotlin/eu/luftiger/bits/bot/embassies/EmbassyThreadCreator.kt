package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.bot.backend.EmbassyBackend
import eu.luftiger.bits.shared.EmbassyThreadCreationEvent
import eu.luftiger.bits.shared.EmbassyThreadCreationReport
import eu.luftiger.bits.shared.ThreadCreationFailure
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Creates the embassy thread an admin asked for and tells the backend about it, which assigns it to the country.
 *
 * The backend gives up after a minute, so an event older than that is dropped, and the report is tried only a few times
 * within it. An event that comes again creates no second thread: the bot remembers the requests it handled, and a thread
 * the backend no longer wants (410), e.g. after a restart of the bot or a late answer, is deleted again.
 */
class EmbassyThreadCreator(
    private val factory: EmbassyThreadFactory,
    private val backend: EmbassyBackend,
    private val clock: Clock = Clock.systemUTC(),
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val handled = LinkedHashSet<Long>()

    suspend fun handle(event: EmbassyThreadCreationEvent) {
        synchronized(handled) {
            if (!handled.add(event.creationId)) return
            if (handled.size > MAX_REMEMBERED) handled.remove(handled.first())
        }
        if (clock.millis() - event.occurredAt > TIMEOUT_MILLIS) {
            log.warn("Embassy thread creation {} is older than a minute, the backend gave up on it", event.creationId)
            return
        }
        val report =
            try {
                when (val result = factory.createPrivateThread(event.channelId, event.name, "BITS: Botschaft von ${event.countryName}")) {
                    is ThreadCreation.Created -> EmbassyThreadCreationReport(thread = result.thread)
                    ThreadCreation.ChannelMissing -> EmbassyThreadCreationReport(failure = ThreadCreationFailure.CHANNEL_MISSING)
                    ThreadCreation.NoPermission -> EmbassyThreadCreationReport(failure = ThreadCreationFailure.NO_PERMISSION)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Discord refused to create embassy thread {}", event.name, e)
                EmbassyThreadCreationReport(failure = ThreadCreationFailure.FAILED)
            }
        val thread = report.thread
        if (!send(event.creationId, report) && thread != null) {
            try {
                factory.deleteThread(thread.id, "BITS: Botschafts-Thread wird nicht mehr gebraucht")
                log.info("Deleted embassy thread {} that the backend no longer wanted", thread.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Could not delete embassy thread {} that the backend no longer wanted", thread.id, e)
            }
        }
    }

    /** `false` if the backend no longer wants the thread; other failures are logged. */
    private suspend fun send(
        creationId: Long,
        report: EmbassyThreadCreationReport,
    ): Boolean {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                backend.reportThreadCreation(creationId, report)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.Gone) return false
                log.warn("The backend rejected embassy thread creation {} with {}", creationId, e.response.status)
                return true
            } catch (e: Exception) {
                log.warn("Reporting embassy thread creation {} failed (attempt {})", creationId, attempt + 1, e)
            }
            if (attempt < MAX_ATTEMPTS - 1) delay(retryDelayMillis)
        }
        log.error("Gave up reporting embassy thread creation {}; the thread stays for assigning it by hand", creationId)
        return true
    }

    private companion object {
        /** As long as the backend waits. */
        const val TIMEOUT_MILLIS = 60_000L
        const val MAX_ATTEMPTS = 4
        const val RETRY_DELAY_MILLIS = 5_000L
        const val MAX_REMEMBERED = 1_000
    }
}
