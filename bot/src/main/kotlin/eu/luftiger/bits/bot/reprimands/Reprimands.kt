package eu.luftiger.bits.bot.reprimands

import eu.luftiger.bits.bot.accounts.DirectMessages
import eu.luftiger.bits.bot.backend.ReprimandBackend
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.ProtectedCause
import eu.luftiger.bits.shared.ReprimandMember
import eu.luftiger.bits.shared.ReprimandRequest
import eu.luftiger.bits.shared.ReprimandResponse
import eu.luftiger.bits.shared.ReprimandTimeoutEvent
import eu.luftiger.bits.shared.ReprimandTimeoutProblemReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.entities.MessageEmbed
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * `/reprimand` and what follows it. The backend decides whether the member may and counts the reprimand; the bot then
 * sets the timeout, writes to the reprimanded member and posts the reprimand in the channel. A timeout that does not
 * work and a direct message that does not arrive do not stop the rest.
 */
class Reprimands(
    private val backend: ReprimandBackend,
    private val guild: ReprimandGuild,
    private val directMessages: DirectMessages,
    private val language: suspend (discordUserId: String) -> Language,
    private val guildName: () -> String,
    /** Sends a report to the backend in the background, trying again if it fails. */
    private val report: (what: String, call: suspend () -> Unit) -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Reprimands [target] and returns the confirmation for [actor], in [actorLanguage]. [post] sends the public message
     * to the channel of the command. A backend that cannot be reached throws.
     */
    suspend fun reprimand(
        actor: ReprimandMember,
        target: ReprimandMember,
        mention: String,
        reason: String,
        post: suspend (MessageEmbed) -> Unit,
        actorLanguage: Language,
    ): String {
        val reprimanded =
            when (val response = backend.reprimand(ReprimandRequest(actor, target, reason))) {
                is ReprimandResponse.Reprimanded -> response
                ReprimandResponse.Forbidden -> return forbiddenMessage(actorLanguage)
                ReprimandResponse.Disabled -> return disabledMessage(actorLanguage)
                is ReprimandResponse.Protected -> return protectedMessage(response.cause, actorLanguage)
            }
        val text = reason.trim()
        val timedOut = timeout(target, reprimanded)
        val timeoutMinutes = reprimanded.timeoutMinutes.takeIf { timedOut }
        val dmSent =
            withTimeoutOrNull(DIRECT_MESSAGE_TIMEOUT_MILLIS) {
                attempt("send a direct message about a reprimand to user ${target.discordUserId}") {
                    val language = language(target.discordUserId)
                    directMessages.send(
                        target.discordUserId,
                        ReprimandMessages.directMessage(guildName(), text, reprimanded.count, timeoutMinutes, language),
                    )
                }
            } ?: false
        val posted =
            attempt("post a reprimand of user ${target.discordUserId}") {
                post(ReprimandMessages.public(mention, text, reprimanded.count, timeoutMinutes))
            }
        return confirmation(target.name, reprimanded, timedOut, dmSent, posted, actorLanguage)
    }

    /** Sets the timeout a withdrawn reprimand leaves; a member who left is skipped. */
    suspend fun onTimeout(event: ReprimandTimeoutEvent) {
        val name = guild.memberName(event.discordUserId) ?: return
        val until = event.until?.let(Instant::ofEpochMilli)?.takeIf { it > now() }
        try {
            if (until == null) guild.removeTimeout(event.discordUserId) else guild.timeoutUntil(event.discordUserId, until)
        } catch (e: TimeoutFailed) {
            log.warn("Could not change the timeout of user {} after a withdrawn reprimand", event.discordUserId, e)
            reportProblem(event.discordUserId, name, "Der Timeout ließ sich nach der Rücknahme einer Rüge nicht ändern. ${e.reason}", null)
        }
    }

    private suspend fun timeout(
        target: ReprimandMember,
        reprimanded: ReprimandResponse.Reprimanded,
    ): Boolean =
        try {
            guild.timeoutUntil(target.discordUserId, Instant.ofEpochMilli(reprimanded.timeoutUntil))
            true
        } catch (e: TimeoutFailed) {
            log.warn("Could not time out user {} after reprimand {}", target.discordUserId, reprimanded.reprimandId, e)
            reportProblem(
                target.discordUserId,
                target.name,
                "Die ${reprimanded.count}. Rüge zählt, aber der Timeout ließ sich nicht setzen. ${e.reason}",
                reprimanded.reprimandId,
            )
            false
        }

    private fun reportProblem(
        discordUserId: String,
        name: String,
        reason: String,
        reprimandId: Long?,
    ) = report("a timeout problem of user $discordUserId") {
        backend.reportReprimandTimeoutProblem(ReprimandTimeoutProblemReport(discordUserId, name, reason, reprimandId))
    }

    private suspend fun attempt(
        what: String,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not {}", what, e)
            false
        }

    private fun confirmation(
        name: String,
        reprimanded: ReprimandResponse.Reprimanded,
        timedOut: Boolean,
        dmSent: Boolean,
        posted: Boolean,
        language: Language,
    ): String {
        val count = reprimanded.count
        val lines =
            listOfNotNull(
                if (timedOut) {
                    language.text(
                        "$name ist gerügt ($count. Rüge) und hat einen Timeout von " +
                            "${ReprimandMessages.duration(reprimanded.timeoutMinutes, language)}.",
                        "$name is reprimanded (reprimand number $count) and timed out for " +
                            "${ReprimandMessages.duration(reprimanded.timeoutMinutes, language)}.",
                    )
                } else {
                    language.text(
                        "$name ist gerügt ($count. Rüge), aber den Timeout konnte ich nicht setzen, z. B. weil das " +
                            "Mitglied über meiner Rolle steht. Das System-Log hält es fest.",
                        "$name is reprimanded (reprimand number $count), but I could not set the timeout, e.g. because " +
                            "the member is above my role. The system log records it.",
                    )
                },
                if (dmSent) {
                    null
                } else {
                    language.text(
                        "Die Direktnachricht kam nicht an, z. B. weil das Mitglied keine annimmt.",
                        "The direct message did not arrive, e.g. because the member does not accept any.",
                    )
                },
                if (posted) {
                    null
                } else {
                    language.text(
                        "In diesem Kanal darf ich die Rüge nicht posten.",
                        "I may not post the reprimand in this channel.",
                    )
                },
            )
        return lines.joinToString(" ")
    }

    companion object {
        private const val DIRECT_MESSAGE_TIMEOUT_MILLIS = 5_000L

        fun forbiddenMessage(language: Language): String =
            language.text(
                "Rügen dürfen nur Admins und Mitglieder der Rollen, die im Dashboard dafür freigegeben sind.",
                "Only admins and members of the roles allowed in the dashboard may reprimand.",
            )

        fun disabledMessage(language: Language): String =
            language.text("Rügen sind gerade ausgeschaltet.", "Reprimands are switched off at the moment.")

        fun notMemberMessage(language: Language): String =
            language.text("Das Mitglied ist nicht auf dem Server.", "The member is not on the server.")

        private fun protectedMessage(
            cause: ProtectedCause,
            language: Language,
        ): String =
            when (cause) {
                ProtectedCause.SELF -> language.text("Du kannst dich nicht selbst rügen.", "You cannot reprimand yourself.")
                ProtectedCause.BOT -> language.text("Bots lassen sich nicht rügen.", "Bots cannot be reprimanded.")
                ProtectedCause.ADMIN -> language.text("Admins lassen sich nicht rügen.", "Admins cannot be reprimanded.")
                ProtectedCause.REPRIMANDER ->
                    language.text(
                        "Mitglieder, die selbst rügen dürfen, lassen sich nicht rügen.",
                        "Members who may reprimand others cannot be reprimanded.",
                    )
            }
    }
}
