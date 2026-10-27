package eu.luftiger.bits.bot.features

import eu.luftiger.bits.bot.backend.FeatureBackend
import eu.luftiger.bits.shared.FeatureStates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Which features an admin left on, as the backend said last. Asked every [PERIOD_MILLIS], so a switch reaches the bot
 * within that time; until the first answer everything is on. While the backend cannot be reached, the last answer
 * stays, and commands fail at the backend anyway.
 */
class Features(
    private val backend: FeatureBackend,
    private val scope: CoroutineScope,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    var states = FeatureStates()
        private set

    @Volatile
    private var failing = false

    suspend fun refresh() {
        try {
            states = backend.features()
            if (failing) log.info("Feature states can be loaded again")
            failing = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Once per outage, not every poll.
            if (!failing) log.warn("Could not load the feature states, keeping the last ones", e)
            failing = true
        }
    }

    fun refreshPeriodically() {
        scope.launch {
            while (true) {
                refresh()
                delay(PERIOD_MILLIS)
            }
        }
    }

    private companion object {
        const val PERIOD_MILLIS = 30_000L
    }
}
