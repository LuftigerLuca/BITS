package eu.luftiger.bits.bot.commands

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Verification checks whose result the backend sends later as event, by code. Each waits at most [timeout]; then
 * [onTimeout] gets its reply target, and a result that comes after that is dropped. Kept in memory only, so after a
 * restart of the bot the results of earlier checks find nobody to answer.
 */
class PendingChecks<T : Any>(
    private val scope: CoroutineScope,
    private val timeout: Duration,
    private val onTimeout: (T) -> Unit,
) {
    private val pending = ConcurrentHashMap<String, Pair<T, Job>>()

    fun add(
        code: String,
        replyTo: T,
    ) {
        val timeoutJob =
            scope.launch {
                delay(timeout.toMillis())
                pending.remove(code)?.let { (target, _) -> onTimeout(target) }
            }
        pending.put(code, replyTo to timeoutJob)?.second?.cancel()
    }

    /** The reply target of the check of [code], or `null` if it timed out or is unknown. */
    fun complete(code: String): T? =
        pending.remove(code)?.let { (target, timeoutJob) ->
            timeoutJob.cancel()
            target
        }
}
