package eu.luftiger.bits.backend.systemlog.api

import org.springframework.modulith.NamedInterface

/** What the bot did with an embassy sync; see [SystemLog.recordEmbassySyncResult]. */
@NamedInterface("api")
data class EmbassySyncResult(
    /** Threads the bot brought to the target state. */
    val syncedThreadCount: Int,
    val changes: List<EmbassyChange>,
)

/** A member added to or removed from a thread, or given or deprived of the ambassador role. */
@NamedInterface("api")
data class EmbassyChange(
    val discordUserId: String,
    val memberName: String,
    /** For changes of the role only. */
    val roleId: String?,
    val roleName: String?,
    val outcome: EmbassyChangeOutcome,
    /** German, e.g. the thread and why the member is in it, or why the change failed. */
    val reason: String?,
)

@NamedInterface("api")
enum class EmbassyChangeOutcome {
    THREAD_ADDED,
    THREAD_REMOVED,
    ROLE_GRANTED,
    ROLE_REMOVED,
    SKIPPED,
    FAILED,
}
