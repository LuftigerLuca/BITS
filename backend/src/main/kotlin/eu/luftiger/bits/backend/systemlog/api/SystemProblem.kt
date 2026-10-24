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

    /** Events for the bot could not be written to Redis for a while. */
    @NamedInterface("api")
    data class OutboxStuck(
        val count: Int,
    ) : SystemProblem
}
