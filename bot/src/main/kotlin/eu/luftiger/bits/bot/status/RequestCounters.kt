package eu.luftiger.bits.bot.status

import eu.luftiger.bits.shared.BackendCallCount
import eu.luftiger.bits.shared.DiscordRequestCount
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/** The bot's requests to the backend since it started, by endpoint, for its [eu.luftiger.bits.shared.BotHeartbeat]. */
class BackendCallCounter {
    private val counts = HashMap<String, LongArray>()

    enum class Outcome { SUCCEEDED, FAILED, TIMED_OUT, UNREACHABLE }

    /** [path] is the path of the request; IDs in it are replaced, so that all votes count as one endpoint. */
    @Synchronized
    fun record(
        method: String,
        path: String,
        outcome: Outcome,
    ) {
        counts.getOrPut("$method ${endpoint(path)}") { LongArray(Outcome.entries.size) }[outcome.ordinal]++
    }

    @Synchronized
    fun snapshot(): List<BackendCallCount> =
        counts.map { (endpoint, outcomes) ->
            BackendCallCount(
                endpoint = endpoint,
                succeeded = outcomes[Outcome.SUCCEEDED.ordinal],
                failed = outcomes[Outcome.FAILED.ordinal],
                timedOut = outcomes[Outcome.TIMED_OUT.ordinal],
                unreachable = outcomes[Outcome.UNREACHABLE.ordinal],
            )
        }

    private companion object {
        fun endpoint(path: String): String =
            "/" +
                path.trim('/').split('/').joinToString("/") { segment ->
                    if (segment.all(Char::isDigit) &&
                        segment.isNotEmpty()
                    ) {
                        "{id}"
                    } else {
                        segment
                    }
                }
    }
}

/**
 * Counts the answers of Discord's REST API to the bot, as an interceptor of JDA's HTTP client. JDA waits out a 429 and
 * sends the request again, which then counts once more.
 */
class DiscordRequestCounter : Interceptor {
    private val total = AtomicLong()
    private val rateLimited = AtomicLong()
    private val refused = AtomicLong()
    private val serverErrors = AtomicLong()

    override fun intercept(chain: Interceptor.Chain): Response {
        total.incrementAndGet()
        val response = chain.proceed(chain.request())
        when {
            response.code == TOO_MANY_REQUESTS -> rateLimited.incrementAndGet()
            response.code >= SERVER_ERROR -> serverErrors.incrementAndGet()
            response.code >= CLIENT_ERROR -> refused.incrementAndGet()
        }
        return response
    }

    fun snapshot() = DiscordRequestCount(total.get(), rateLimited.get(), refused.get(), serverErrors.get())

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val CLIENT_ERROR = 400
        const val SERVER_ERROR = 500
    }
}
