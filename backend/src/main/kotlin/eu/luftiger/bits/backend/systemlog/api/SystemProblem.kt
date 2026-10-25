package eu.luftiger.bits.backend.systemlog.api

import org.springframework.modulith.NamedInterface

/** Something that went wrong without anybody seeing it right away; see [SystemLog.recordProblem]. */
@NamedInterface("api")
sealed interface SystemProblem {
    /** The WarEra data of these members could not be loaded in a sync; their roles stay as they are. */
    @NamedInterface("api")
    data class WareraUsersUnavailable(
        val discordUserIds: List<String>,
    ) : SystemProblem

    /** The government of a country could not be loaded in a sync; its office rules were not run. */
    @NamedInterface("api")
    data class GovernmentUnavailable(
        val countryId: String,
        val countryName: String?,
    ) : SystemProblem

    /** The parties of a country could not be loaded in a sync; the rules for [partyNames] were not run. */
    @NamedInterface("api")
    data class PartiesUnavailable(
        val partyNames: List<String>,
    ) : SystemProblem

    /**
     * The government of a country with an embassy thread could not be loaded; nobody was removed from the thread
     * because of their office. Recorded once per sync.
     */
    @NamedInterface("api")
    data class EmbassyGovernmentUnavailable(
        val countryId: String,
        val countryName: String,
    ) : SystemProblem

    /** The bot could not sync an embassy thread; the other threads were synced. */
    @NamedInterface("api")
    data class EmbassyThreadUnavailable(
        val threadName: String,
        val countryName: String,
        val cause: EmbassyThreadCause,
    ) : SystemProblem

    /** A role with rules was deleted in Discord or moved above the bot, so its rules no longer run. */
    @NamedInterface("api")
    data class RoleNotAssignable(
        val roleId: String,
        /** `null` if the role was deleted. */
        val roleName: String?,
    ) : SystemProblem

    /** A sync failed before it could publish anything. */
    @NamedInterface("api")
    data class SyncAborted(
        val trigger: SyncTrigger,
        val error: String,
    ) : SystemProblem

    /**
     * A member list from the bot would have deleted [departedCount] of [linkedCount] links at once, which looks more
     * like a broken list than like that many members leaving; nothing was deleted.
     */
    @NamedInterface("api")
    data class MemberReportRefused(
        val departedCount: Int,
        val linkedCount: Int,
    ) : SystemProblem

    /**
     * The result of a vote could not be worked out, e.g. because WarEra is down; it is tried again until it works.
     * Recorded once per vote.
     */
    @NamedInterface("api")
    data class VoteEvaluationDelayed(
        val question: String,
        val error: String,
    ) : SystemProblem

    /** Events for the bot could not be written to Redis for a while. */
    @NamedInterface("api")
    data class OutboxStuck(
        val count: Int,
    ) : SystemProblem
}

@NamedInterface("api")
enum class EmbassyThreadCause {
    /** Deleted, or the bot cannot see it. */
    MISSING,

    /** The bot may not manage threads or send messages in threads in its channel. */
    NO_PERMISSION,

    /** Discord refused to list its members or to unarchive it. */
    FAILED,
}
