package eu.luftiger.bits.bot.verifypanel

import eu.luftiger.bits.bot.backend.VerifyPanelBackend
import eu.luftiger.bits.bot.commands.VerifyCommand
import eu.luftiger.bits.shared.VerifyPanelMessage
import eu.luftiger.bits.shared.VerifyPanelProblemReport
import eu.luftiger.bits.shared.VerifyPanelReport
import eu.luftiger.bits.shared.VerifyPanelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import org.slf4j.LoggerFactory

/**
 * Keeps a message with a button to verify in the channel an admin set for new members: posts it, moves it when the
 * channel changes instead of posting a second one, posts it again when it was deleted and brings an old text up to
 * date. The button is handled by [VerifyCommand]. Asks the backend for the state every [PERIOD_MILLIS], so a change
 * arrives within that time; while the backend is down, the last answer stays.
 */
class VerifyPanel(
    private val backend: VerifyPanelBackend,
    private val guild: VerifyPanelGuild,
    /** Sends a report to the backend in the background, trying again if it fails. */
    private val report: (what: String, call: suspend () -> Unit) -> Unit,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mutex = Mutex()

    private var state = VerifyPanelState()
    private var messageUnreported = false

    /** The message known to exist, so that not every poll has to ask Discord. */
    @Volatile
    private var verifiedMessageId: String? = null
    private var lastProblem: String? = null
    private var failing = false

    fun messageDeleted(messageId: String) {
        if (verifiedMessageId == messageId) verifiedMessageId = null
    }

    suspend fun refresh() {
        try {
            val fetched = backend.verifyPanel()
            mutex.withLock {
                // A message the backend has not taken yet is newer than what it answers.
                state = if (messageUnreported) fetched.copy(message = state.message) else fetched
                sync()
            }
            if (failing) log.info("The state of the verify panel can be loaded again")
            failing = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Once per outage, not every poll.
            if (!failing) log.warn("Could not load the state of the verify panel, keeping the last one", e)
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

    /** Brings the message to where the channel is. Only what the backend answers can throw out of here. */
    private suspend fun sync() {
        if (messageUnreported) sendMessage(state.message)
        val target = state.channelId
        // A message that cannot be deleted is reported and left behind, so the new channel does not wait for it.
        state.message?.takeIf { it.channelId != target }?.let { old ->
            discord { guild.delete(old) }
            setMessage(null)
        }
        if (target == null) return
        val message = state.message
        val present =
            when {
                message == null -> false
                message.messageId == verifiedMessageId -> true
                else -> discord { guild.update(message, embed(), button()) } ?: return
            }
        if (present) {
            verifiedMessageId = message?.messageId
            return
        }
        val id = discord { guild.post(target, embed(), button()) } ?: return
        verifiedMessageId = id
        lastProblem = null
        setMessage(VerifyPanelMessage(target, id))
    }

    private suspend fun setMessage(message: VerifyPanelMessage?) {
        state = state.copy(message = message)
        messageUnreported = true
        sendMessage(message)
    }

    private suspend fun sendMessage(message: VerifyPanelMessage?) {
        backend.reportVerifyPanelMessage(VerifyPanelReport(message))
        messageUnreported = false
    }

    /** Runs [call] against Discord; a failure goes to the system log once and returns `null`. */
    private suspend fun <T : Any> discord(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not keep the verify panel", e)
            val reason =
                when (e) {
                    is VerifyPanelChannelGone -> CHANNEL_GONE
                    is InsufficientPermissionException -> NO_PERMISSION
                    else -> REFUSED
                }
            if (lastProblem != "${state.channelId}: $reason") {
                lastProblem = "${state.channelId}: $reason"
                report("a verify panel problem") { backend.reportVerifyPanelProblem(VerifyPanelProblemReport(reason)) }
            }
            null
        }

    internal companion object {
        const val PERIOD_MILLIS = 30_000L
        private const val COLOR = 0x5865F2

        /** In both languages, since everybody reads the same message; what follows the click is in the member's language. */
        fun embed(): MessageEmbed =
            EmbedBuilder()
                .setTitle("Willkommen! / Welcome!")
                .setColor(COLOR)
                .setDescription(
                    "Verknüpfe deinen Discord-Account mit deinem WarEra-Account, um die Rollen dieses Servers zu bekommen. Klicke " +
                        "auf „Jetzt verifizieren“ und gib deinen Namen in WarEra ein. Dafür brauchst du eine Firma in WarEra. Statt " +
                        "des Buttons geht auch `/${VerifyCommand.NAME}`.\n\n" +
                        "Link your Discord account to your WarEra account to get the roles of this server. Click “Verify now” and " +
                        "enter your name in WarEra. You need a company in WarEra for this. Instead of the button, you can also use " +
                        "`/${VerifyCommand.NAME}`.",
                ).build()

        fun button(): Button = Button.primary(VerifyCommand.PANEL_BUTTON, "Jetzt verifizieren / Verify now")

        const val CHANNEL_GONE = "Den Verifizierungs-Kanal gibt es nicht mehr."
        const val NO_PERMISSION = "Dem Bot fehlen im Verifizierungs-Kanal Rechte für seine Nachricht."
        const val REFUSED = "Discord hat die Nachricht im Verifizierungs-Kanal abgelehnt."
    }
}
