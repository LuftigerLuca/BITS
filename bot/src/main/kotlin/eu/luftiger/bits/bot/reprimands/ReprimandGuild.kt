package eu.luftiger.bits.bot.reprimands

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.exceptions.HierarchyException
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.api.requests.ErrorResponse
import java.time.Instant

/** What reprimands need from Discord. */
interface ReprimandGuild {
    /** @throws TimeoutFailed if Discord does not take it */
    suspend fun timeoutUntil(
        discordUserId: String,
        until: Instant,
    )

    /** Succeeds as well if the member has no timeout; @throws TimeoutFailed if Discord does not take it */
    suspend fun removeTimeout(discordUserId: String)

    /** `null` if the member is not on the server. */
    fun memberName(discordUserId: String): String?
}

/** A timeout Discord did not take; [reason] says why in German, for the system log. */
class TimeoutFailed(
    val reason: String,
    cause: Throwable? = null,
) : Exception(reason, cause)

class JdaReprimandGuild(
    private val jda: JDA,
    private val guildId: String,
) : ReprimandGuild {
    private val guild: Guild
        get() = jda.getGuildById(guildId) ?: error("Bot is not a member of guild $guildId")

    override suspend fun timeoutUntil(
        discordUserId: String,
        until: Instant,
    ) = discord {
        guild
            .timeoutUntil(UserSnowflake.fromId(discordUserId), until)
            .reason(AUDIT_REASON)
            .submit()
            .await()
    }

    override suspend fun removeTimeout(discordUserId: String) =
        discord {
            try {
                guild
                    .removeTimeout(UserSnowflake.fromId(discordUserId))
                    .reason(AUDIT_REASON_WITHDRAWN)
                    .submit()
                    .await()
            } catch (e: ErrorResponseException) {
                if (e.errorResponse != ErrorResponse.UNKNOWN_MEMBER) throw e
            }
        }

    override fun memberName(discordUserId: String): String? = guild.getMemberById(discordUserId)?.effectiveName

    private suspend fun discord(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: HierarchyException) {
            throw TimeoutFailed("Das Mitglied steht über der höchsten Rolle des Bots.", e)
        } catch (e: InsufficientPermissionException) {
            throw TimeoutFailed("Dem Bot fehlt das Recht „${Permission.MODERATE_MEMBERS.getName()}“.", e)
        } catch (e: ErrorResponseException) {
            throw TimeoutFailed("Discord hat die Anfrage abgelehnt: ${e.meaning}", e)
        }
    }

    private companion object {
        /** Shown in Discord's audit log. */
        const val AUDIT_REASON = "BITS: Rüge"
        const val AUDIT_REASON_WITHDRAWN = "BITS: Rüge zurückgenommen"
    }
}
