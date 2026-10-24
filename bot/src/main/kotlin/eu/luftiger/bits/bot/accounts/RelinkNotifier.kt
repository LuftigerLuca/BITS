package eu.luftiger.bits.bot.accounts

import eu.luftiger.bits.shared.AccountLinkedEvent
import kotlinx.coroutines.CancellationException
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import org.slf4j.LoggerFactory

/**
 * Tells the previous Discord account of a WarEra account that another Discord account took it over. That is fine when
 * somebody moves to a new Discord account, but it is also what a takeover looks like, and the previous owner would
 * otherwise only notice that their roles are gone.
 *
 * A message that cannot be sent, e.g. because the user does not accept direct messages, is logged and dropped, so the
 * event does not stay pending. Events can arrive twice, so the notifier remembers the ones it handled.
 */
class RelinkNotifier(
    private val directMessages: DirectMessages,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val handled =
        object : LinkedHashMap<String, Unit>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>) = size > REMEMBERED_EVENTS
        }

    suspend fun handle(event: AccountLinkedEvent) {
        val previous = event.previousDiscordUserId ?: return
        val key = "$previous:${event.wareraUserId}:${event.occurredAt}"
        synchronized(handled) { if (handled.put(key, Unit) != null) return }
        try {
            directMessages.send(previous, message(event))
            log.info("Told user {} that their WarEra account was linked to another Discord account", previous)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Cannot tell user {} that their WarEra account was relinked: {}", previous, e.message)
        }
    }

    companion object {
        private const val REMEMBERED_EVENTS = 1000

        internal fun message(event: AccountLinkedEvent): String {
            val account = event.wareraUsername?.let { "dem WarEra-Account **${MarkdownSanitizer.escape(it)}**" } ?: "deinem WarEra-Account"
            val at = event.occurredAt / 1000
            return "Dein Discord-Account ist nicht mehr mit $account verknüpft: Am <t:$at:f> hat sich ein anderer Discord-Account " +
                "dafür verifiziert. Die Rollen, die BITS vergibt, hast du deshalb verloren.\n\n" +
                "Warst du das nicht selbst, wende dich an die Admins des Servers, besonders wenn dich jemand gebeten hat, einen " +
                "Code in einen Firmennamen zu schreiben."
        }
    }
}
