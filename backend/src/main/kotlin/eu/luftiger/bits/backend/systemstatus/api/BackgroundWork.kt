package eu.luftiger.bits.backend.systemstatus.api

import org.springframework.modulith.NamedInterface

/** Work of other modules that runs on a thread of its own, not as part of a request. */
@NamedInterface("api")
enum class BackgroundJob {
    /** Loads every linked user from WarEra, before the hourly sync of all members. */
    ROLE_SYNC_REFRESH,
    ROLE_SYNC,
    EMBASSY_SYNC,
    VOTE_EVALUATION,

    /** Looks for the code of a verification in the WarEra profile. */
    VERIFICATION_CHECK,
}

/**
 * Runs background work so the page "System-Status" shows when it ran, how long and with which result, and so the
 * WarEra calls it makes count for it.
 */
@NamedInterface("api")
interface BackgroundWork {
    /**
     * Runs [work] on the calling thread. [trigger] says why it runs, e.g. the name of a
     * [eu.luftiger.bits.backend.systemlog.api.SyncTrigger]. An exception of [work] is recorded and thrown on.
     */
    fun <T> run(
        job: BackgroundJob,
        trigger: String? = null,
        work: () -> T,
    ): T
}
