package eu.luftiger.bits.bot.systemlog

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.EmbassyBackend
import eu.luftiger.bits.shared.EmbassySyncReport
import eu.luftiger.bits.shared.EmbassyThreadRenameReport
import eu.luftiger.bits.shared.RoleSyncReport
import eu.luftiger.bits.shared.UndeliveredEventReport
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Sends what the bot did to the backend's system log in the background, so that a slow or unreachable backend never
 * holds up the event at hand. A report the backend does not take is sent again with backoff for about an hour; one it
 * rejects (4xx), e.g. for a sync whose run was deleted already, is dropped.
 */
class SystemLogReporter(
    private val backend: BackendClient,
    private val scope: CoroutineScope,
    private val embassyBackend: EmbassyBackend,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun reportRoleSync(
        syncId: Long,
        report: RoleSyncReport,
    ) = send("the result of role sync $syncId") { backend.reportRoleSync(syncId, report) }

    fun reportEmbassySync(
        syncId: Long,
        report: EmbassySyncReport,
    ) = send("the result of embassy sync $syncId") { embassyBackend.reportEmbassySync(syncId, report) }

    fun reportThreadRenames(report: EmbassyThreadRenameReport) =
        send("${report.failures.size} failed embassy thread renames") { embassyBackend.reportThreadRenames(report) }

    fun reportUndeliveredEvent(report: UndeliveredEventReport) =
        send("undelivered event ${report.streamId}") { backend.reportUndeliveredEvent(report) }

    private fun send(
        what: String,
        call: suspend () -> Unit,
    ) {
        scope.launch {
            var retryDelay = MIN_RETRY_MILLIS
            repeat(MAX_ATTEMPTS) { attempt ->
                try {
                    call()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ClientRequestException) {
                    log.warn("The backend rejected {} with {}, dropping it", what, e.response.status)
                    return@launch
                } catch (e: Exception) {
                    if (attempt == MAX_ATTEMPTS - 1) {
                        log.error("Reporting {} to the backend failed {} times, giving up", what, MAX_ATTEMPTS, e)
                        return@launch
                    }
                    log.warn("Reporting {} to the backend failed, trying again in {} ms", what, retryDelay, e)
                }
                delay(retryDelay)
                retryDelay = minOf(retryDelay * 2, MAX_RETRY_MILLIS)
            }
        }
    }

    internal companion object {
        const val MIN_RETRY_MILLIS = 2_000L
        const val MAX_RETRY_MILLIS = 60_000L

        /** With the backoff, attempts for about an hour. */
        const val MAX_ATTEMPTS = 65
    }
}
