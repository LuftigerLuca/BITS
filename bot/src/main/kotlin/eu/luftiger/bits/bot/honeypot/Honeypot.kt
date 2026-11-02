package eu.luftiger.bits.bot.honeypot

import eu.luftiger.bits.bot.accounts.DirectMessages
import eu.luftiger.bits.bot.backend.HoneypotBackend
import eu.luftiger.bits.shared.HoneypotKickOutcome
import eu.luftiger.bits.shared.HoneypotKickReport
import eu.luftiger.bits.shared.HoneypotPauseReport
import eu.luftiger.bits.shared.HoneypotProblemReport
import eu.luftiger.bits.shared.HoneypotState
import eu.luftiger.bits.shared.HoneypotWarning
import eu.luftiger.bits.shared.HoneypotWarningReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import org.slf4j.LoggerFactory

/** A message in a channel, reduced to what the honeypot decides by. */
data class HoneypotMessage(
    val channelId: String,
    val messageId: String,
    val authorId: String,
    val authorName: String,
    /** `false` for the owner and members above the bot's highest role. */
    val kickable: Boolean,
)

/**
 * Removes whoever writes in the channel an admin set as honeypot and keeps the bot's warning there. Asks the backend for
 * the state every [PERIOD_MILLIS], so a change arrives within that time; while the backend is down, the last answer
 * stays. Messages are handled one at a time, so the limit of [MAX_KICKS] kicks within [WINDOW_MILLIS] holds during a
 * wave of bots. Reaching it pauses the kicks until an admin lifts the pause, since it may also mean that a normal channel
 * was picked by mistake.
 */
class Honeypot(
    private val backend: HoneypotBackend,
    private val guild: HoneypotGuild,
    private val directMessages: DirectMessages,
    private val guildName: () -> String,
    /** Sends a report to the backend in the background, trying again if it fails. */
    private val report: (what: String, call: suspend () -> Unit) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mutex = Mutex()

    @Volatile
    private var state = HoneypotState()

    /** Until the backend took it, which [refresh] tries each time; only then can an admin see and lift the pause. */
    private var pendingPause: HoneypotPauseReport? = null
    private var warningUnreported = false

    /** The warning known to exist, so that not every poll has to ask Discord. */
    @Volatile
    private var verifiedWarningId: String? = null
    private var lastProblem: String? = null
    private var failing = false

    /** Time and member of the kicks within [WINDOW_MILLIS]. */
    private val kicks = ArrayDeque<Pair<Long, String>>()

    private val paused get() = state.kicksPaused || pendingPause != null

    /** Whether a message in [channelId] needs handling at all; threads of the honeypot have their own ID. */
    fun watches(channelId: String) = state.channelId == channelId

    fun warningDeleted(messageId: String) {
        if (verifiedWarningId == messageId) verifiedWarningId = null
    }

    suspend fun refresh() {
        try {
            val pause = mutex.withLock { pendingPause }
            pause?.let { backend.reportHoneypotPause(it) }
            val fetched = backend.honeypot()
            mutex.withLock {
                apply(fetched, pause)
                syncWarning()
            }
            if (failing) log.info("The honeypot state can be loaded again")
            failing = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Once per outage, not every poll.
            if (!failing) log.warn("Could not load the honeypot state, keeping the last one", e)
            failing = true
        }
    }

    fun refreshPeriodically(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                refresh()
                delay(PERIOD_MILLIS)
            }
        }
    }

    suspend fun handle(message: HoneypotMessage) =
        mutex.withLock {
            if (!watches(message.channelId)) return@withLock
            val notes = mutableListOf<String>()
            attempt { guild.delete(message.channelId, message.messageId) } ?: notes.add(MESSAGE_NOT_DELETED)
            // A bot that posts several messages at once is reported and notified only once.
            if (kicks.any { (time, member) -> member == message.authorId && now() - time < DUPLICATE_MILLIS }) return@withLock
            val outcome =
                when {
                    !message.kickable -> HoneypotKickOutcome.SKIPPED.also { notes.add(0, MEMBER_ABOVE_BOT) }
                    paused -> HoneypotKickOutcome.SKIPPED.also { notes.add(0, KICKS_PAUSED) }
                    !guild.mayKick() -> HoneypotKickOutcome.FAILED.also { notes.add(0, NO_KICK_PERMISSION) }
                    else -> kick(message, notes)
                }
            sendReport(message, outcome, notes)
        }

    private suspend fun kick(
        message: HoneypotMessage,
        notes: MutableList<String>,
    ): HoneypotKickOutcome {
        // Before the kick, since the bot can no longer write to somebody without a common server.
        withTimeoutOrNull(DIRECT_MESSAGE_TIMEOUT_MILLIS) {
            attempt { directMessages.send(message.authorId, kickMessage(guildName())) }
        } ?: notes.add(DIRECT_MESSAGE_FAILED)
        return try {
            guild.kick(message.authorId)
            countKick(message)
            HoneypotKickOutcome.KICKED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not kick {} from the honeypot", message.authorId, e)
            notes.add(0, if (e is InsufficientPermissionException) NO_KICK_PERMISSION else KICK_REFUSED)
            HoneypotKickOutcome.FAILED
        }
    }

    private fun countKick(message: HoneypotMessage) {
        val time = now()
        kicks.addLast(time to message.authorId)
        while (kicks.first().first <= time - WINDOW_MILLIS) kicks.removeFirst()
        if (kicks.size < MAX_KICKS || paused) return
        log.warn("Pausing honeypot kicks after {} kicks", kicks.size)
        pendingPause = HoneypotPauseReport(message.channelId, kicks.size, (WINDOW_MILLIS / 60_000).toInt())
    }

    private fun sendReport(
        message: HoneypotMessage,
        outcome: HoneypotKickOutcome,
        notes: List<String>,
    ) {
        val kickReport =
            HoneypotKickReport(
                message.authorId,
                message.authorName,
                outcome,
                notes.joinToString(" ").ifEmpty { null },
            )
        report("the honeypot result for ${message.authorId}") { backend.reportHoneypotKick(kickReport) }
    }

    /** [reportedPause] reached the backend before it answered with [fetched]; a pause that came up since stays pending. */
    private fun apply(
        fetched: HoneypotState,
        reportedPause: HoneypotPauseReport?,
    ) {
        val channelChanged = fetched.channelId != state.channelId
        val wasPaused = paused
        // A warning the backend has not taken yet is newer than what it answers.
        state = if (warningUnreported) fetched.copy(warning = state.warning) else fetched
        if (channelChanged || pendingPause === reportedPause) pendingPause = null
        if (channelChanged || (wasPaused && !paused)) kicks.clear()
    }

    /** Brings the warning to where the honeypot is. Only what the backend answers can throw out of here. */
    private suspend fun syncWarning() {
        if (warningUnreported) sendWarning(state.warning)
        val target = state.channelId
        state.warning?.takeIf { it.channelId != target }?.let { old ->
            discord { guild.delete(old.channelId, old.messageId) }
            setWarning(null)
        }
        if (target == null) return
        val warning = state.warning
        val present =
            when {
                warning == null -> false
                warning.messageId == verifiedWarningId -> true
                else -> discord { guild.exists(warning) } ?: return
            }
        if (present) {
            verifiedWarningId = warning?.messageId
            return
        }
        val id = discord { guild.post(target, warningEmbed()) } ?: return
        verifiedWarningId = id
        lastProblem = null
        setWarning(HoneypotWarning(target, id))
    }

    private suspend fun setWarning(warning: HoneypotWarning?) {
        state = state.copy(warning = warning)
        warningUnreported = true
        sendWarning(warning)
    }

    private suspend fun sendWarning(warning: HoneypotWarning?) {
        backend.reportHoneypotWarning(HoneypotWarningReport(warning))
        warningUnreported = false
    }

    /** Runs [call] against Discord; a failure goes to the system log once and returns `null`. */
    private suspend fun <T : Any> discord(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not keep the honeypot warning", e)
            val reason =
                when (e) {
                    is HoneypotChannelGone -> CHANNEL_GONE
                    is InsufficientPermissionException -> NO_WARNING_PERMISSION
                    else -> WARNING_REFUSED
                }
            if (lastProblem != "${state.channelId}: $reason") {
                lastProblem = "${state.channelId}: $reason"
                report("a honeypot problem") { backend.reportHoneypotProblem(HoneypotProblemReport(reason)) }
            }
            null
        }

    /** `null` if [call] failed. */
    private suspend fun attempt(call: suspend () -> Unit): Unit? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.debug("A honeypot step failed", e)
            null
        }

    internal companion object {
        const val PERIOD_MILLIS = 30_000L
        const val MAX_KICKS = 10
        const val WINDOW_MILLIS = 600_000L
        private const val DUPLICATE_MILLIS = 5_000L
        private const val DIRECT_MESSAGE_TIMEOUT_MILLIS = 3_000L

        private const val WARNING_COLOR = 0xED4245

        fun warningEmbed(): MessageEmbed =
            EmbedBuilder()
                .setTitle("Nicht in diesen Kanal schreiben!")
                .setColor(WARNING_COLOR)
                .setDescription(
                    "Dieser Kanal fängt Spam-Bots ab. Wer hier schreibt, wird automatisch vom Server gekickt und verliert die " +
                        "Verknüpfung seines WarEra-Accounts.",
                ).build()

        const val MEMBER_ABOVE_BOT = "Das Mitglied steht über der Rolle des Bots."
        const val KICKS_PAUSED = "Die Kicks sind pausiert."
        const val NO_KICK_PERMISSION = "Dem Bot fehlt die Berechtigung „Mitglieder kicken“."
        const val KICK_REFUSED = "Discord hat den Kick abgelehnt."
        const val MESSAGE_NOT_DELETED = "Die Nachricht konnte nicht gelöscht werden."
        const val DIRECT_MESSAGE_FAILED = "Die Direktnachricht konnte nicht zugestellt werden."
        const val CHANNEL_GONE = "Den Honeypot-Kanal gibt es nicht mehr."
        const val NO_WARNING_PERMISSION = "Dem Bot fehlen im Honeypot-Kanal Rechte für die Warnnachricht."
        const val WARNING_REFUSED = "Discord hat die Warnnachricht im Honeypot-Kanal abgelehnt."

        fun kickMessage(guildName: String) =
            "Du wurdest von **$guildName** gekickt, weil von deinem Account eine Nachricht in einem Kanal kam, der Spam-Bots " +
                "abfängt. War das ein Versehen, kannst du wieder beitreten und deinen WarEra-Account mit `/verify` neu " +
                "verknüpfen. Warst du es nicht, ist dein Account vielleicht gehackt: Ändere bitte dein Passwort."
    }
}
