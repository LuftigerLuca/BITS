package eu.luftiger.bits.backend.systemstatus.internal

import eu.luftiger.bits.backend.systemstatus.api.BackgroundJob
import kotlinx.serialization.Serializable

/** What a WarEra call is made for. */
@Serializable
enum class WareraCaller {
    VERIFICATION,
    ROLE_SYNC,
    EMBASSY_SYNC,
    PROFILE,
    ROLE_EXPLANATION,
    VOTE,
    PING,
    DASHBOARD,

    /** Other calls of the bot, e.g. its reports. */
    BOT,

    /** Neither in a request nor in [eu.luftiger.bits.backend.systemstatus.api.BackgroundWork]. */
    OTHER,
    ;

    companion object {
        private val current = ThreadLocal<WareraCaller>()

        fun current(): WareraCaller = current.get() ?: OTHER

        /** Runs [block] with [caller] as the [current] one, and restores the previous one afterwards. */
        fun <T> within(
            caller: WareraCaller,
            block: () -> T,
        ): T {
            val previous = current.get()
            current.set(caller)
            try {
                return block()
            } finally {
                if (previous == null) current.remove() else current.set(previous)
            }
        }

        fun of(job: BackgroundJob): WareraCaller =
            when (job) {
                BackgroundJob.ROLE_SYNC_REFRESH, BackgroundJob.ROLE_SYNC -> ROLE_SYNC
                BackgroundJob.EMBASSY_SYNC -> EMBASSY_SYNC
                BackgroundJob.VOTE_EVALUATION -> VOTE
                BackgroundJob.VERIFICATION_CHECK -> VERIFICATION
            }

        /** By the path of a request; the dashboard is under `/web/`, the bot under `/api/`. */
        fun ofPath(path: String): WareraCaller? =
            when {
                path.startsWith("/web/") -> DASHBOARD
                path.startsWith("/api/verifications") -> VERIFICATION
                path.startsWith("/api/profiles") -> PROFILE
                path.startsWith("/api/role-explanations") -> ROLE_EXPLANATION
                path.startsWith("/api/votes") -> VOTE
                path.startsWith("/api/ping") -> PING
                path.startsWith("/api/") -> BOT
                else -> null
            }
    }
}
