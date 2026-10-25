package eu.luftiger.bits.bot.members

import eu.luftiger.bits.bot.backend.BackendClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

/**
 * Tells the backend who is on the guild, bots left out: the whole list on start and every hour, and each member who
 * joins or leaves. The backend deletes the links of members missing from the list, so the list is only sent once all
 * members are loaded. A list the backend does not take is sent again with backoff, until the next one replaces it; a
 * lost join or leave is caught up with by the next list.
 */
class MemberReporter(
    private val guildId: String,
    private val backend: BackendClient,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private var pending: Job? = null

    /** Reports [guild] now and then every hour. */
    fun reportPeriodically(guild: Guild) {
        scope.launch {
            while (true) {
                report(guild)
                delay(PERIOD_MILLIS)
            }
        }
    }

    fun report(guild: Guild) {
        if (guild.id == guildId) report { if (guild.isLoaded) guild.members.filterNot { it.user.isBot }.map { it.id } else null }
    }

    /**
     * Takes the members only right before each attempt, so a retry sends the current ones. [members] returns `null`
     * while they are not all loaded, which counts as a failed attempt.
     */
    @Synchronized
    internal fun report(members: () -> List<String>?) {
        pending?.cancel()
        pending =
            scope.launch {
                var retryDelay = MIN_RETRY_MILLIS
                while (true) {
                    try {
                        val ids = members()
                        if (ids != null) {
                            backend.reportMembers(ids)
                            return@launch
                        }
                        log.warn("Members of the guild are not loaded yet, trying again in {} ms", retryDelay)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn("Reporting the members to the backend failed, trying again in {} ms", retryDelay, e)
                    }
                    delay(retryDelay)
                    retryDelay = minOf(retryDelay * 2, MAX_RETRY_MILLIS)
                }
            }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        if (event.guild.id != guildId || event.user.isBot) return
        send("join") { backend.reportMemberJoined(event.user.id) }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        if (event.guild.id != guildId || event.user.isBot) return
        val name = event.member?.effectiveName ?: event.user.effectiveName
        send("departure") { backend.reportMemberLeft(event.user.id, name) }
    }

    private fun send(
        what: String,
        call: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Reporting a {} to the backend failed, the next member list catches up", what, e)
            }
        }
    }

    private companion object {
        const val MIN_RETRY_MILLIS = 2_000L
        const val MAX_RETRY_MILLIS = 60_000L
        const val PERIOD_MILLIS = 3_600_000L
    }
}
