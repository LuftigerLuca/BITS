package eu.luftiger.bits.bot.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Sends a report of the guild's state to the backend once things have been quiet for [DEBOUNCE_MILLIS], since one
 * change in Discord can fire many events. A report the backend does not take is sent again with backoff, until a
 * newer one replaces it.
 */
class DebouncedReport<T>(
    private val scope: CoroutineScope,
    /** For the log, e.g. "the roles". */
    private val what: String,
    private val send: suspend (T) -> Unit,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var pending: Job? = null

    /** Takes the state only right before each attempt, so a retry sends the current one. */
    @Synchronized
    fun report(state: () -> T) {
        pending?.cancel()
        pending =
            scope.launch {
                delay(DEBOUNCE_MILLIS)
                var retryDelay = MIN_RETRY_MILLIS
                while (true) {
                    try {
                        send(state())
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn("Reporting {} to the backend failed, trying again in {} ms", what, retryDelay, e)
                    }
                    delay(retryDelay)
                    retryDelay = minOf(retryDelay * 2, MAX_RETRY_MILLIS)
                }
            }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 2_000L
        const val MIN_RETRY_MILLIS = 2_000L
        const val MAX_RETRY_MILLIS = 60_000L
    }
}
