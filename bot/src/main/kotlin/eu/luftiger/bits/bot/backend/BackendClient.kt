package eu.luftiger.bits.bot.backend

import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.DiscordRoleDto
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.shared.RoleSyncReport
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.UndeliveredEventReport
import eu.luftiger.bits.shared.UnlinkAccountResponse

/** Access to the internal REST API of the backend. */
interface BackendClient {
    suspend fun ping(discordUserId: String): PingResponse

    /** @throws WareraUnavailableException if the backend cannot reach the WarEra API */
    suspend fun startVerification(
        discordUserId: String,
        wareraUsername: String,
    ): StartVerificationResponse

    /** @throws WareraUnavailableException if the backend cannot reach the WarEra API */
    suspend fun checkVerification(
        discordUserId: String,
        code: String,
        discordName: String? = null,
    ): CheckVerificationResponse

    /** Deletes the link and any open code of the user. */
    suspend fun unlinkAccount(discordUserId: String): UnlinkAccountResponse

    /** Replaces the roles of the guild known to the backend. */
    suspend fun reportRoles(roles: List<DiscordRoleDto>)

    /** Replaces the members of the guild known to the backend, which deletes the links of everybody missing. */
    suspend fun reportMembers(discordUserIds: List<String>)

    suspend fun reportMemberJoined(discordUserId: String)

    /** The backend deletes the member's link; [memberName] is for its system log. */
    suspend fun reportMemberLeft(
        discordUserId: String,
        memberName: String,
    )

    /** Tells the backend's system log what the bot changed for the sync [syncId]. */
    suspend fun reportRoleSync(
        syncId: Long,
        report: RoleSyncReport,
    )

    /** Tells the backend's system log about an event the bot gave up on. */
    suspend fun reportUndeliveredEvent(report: UndeliveredEventReport)
}

/** The backend did not answer in time; it may still finish what it was asked to do. */
class BackendTimeoutException(
    cause: Throwable,
) : RuntimeException("The backend did not answer in time", cause)

class WareraUnavailableException(
    cause: Throwable,
) : RuntimeException("The backend cannot reach the WarEra API", cause)
