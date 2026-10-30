package eu.luftiger.bits.shared

import kotlinx.serialization.Serializable

/**
 * What the bot reports about itself every 30 seconds, for the page "System-Status". The counts are since [startedAt],
 * so a report the backend misses loses nothing; the backend takes the difference to the previous one.
 */
@Serializable
data class BotHeartbeat(
    /** The commit the bot was built from, `dev` outside of a deployment. */
    val version: String,
    /** Epoch milliseconds. */
    val startedAt: Long,
    /** JDA's status, e.g. `CONNECTED`. */
    val gatewayStatus: String,
    /** `-1` until the first heartbeat of the gateway. */
    val gatewayPingMillis: Long,
    val guildMembers: Int?,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val threads: Int,
    /** Requests to the backend, by endpoint. */
    val backendCalls: List<BackendCallCount>,
    val discordRequests: DiscordRequestCount,
)

/** [endpoint] is method and path, with IDs replaced by `{id}`. */
@Serializable
data class BackendCallCount(
    val endpoint: String,
    val succeeded: Long,
    /** The backend answered with an error. */
    val failed: Long,
    val timedOut: Long,
    /** No connection to the backend. */
    val unreachable: Long,
)

/** Requests of the bot to Discord's REST API, e.g. to change roles. */
@Serializable
data class DiscordRequestCount(
    val total: Long,
    /** Discord answered 429; JDA waits and tries again. */
    val rateLimited: Long,
    /** Other answers of 400 and above, e.g. a role the bot may not give. */
    val refused: Long,
    val serverErrors: Long,
)
