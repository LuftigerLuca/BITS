package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.shared.DiscordRoleDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.guild.member.GenericGuildMemberEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.role.RoleCreateEvent
import net.dv8tion.jda.api.events.role.RoleDeleteEvent
import net.dv8tion.jda.api.events.role.update.GenericRoleUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

/**
 * Tells the backend which roles the guild has and which of them the bot can give: on start, every hour in case a
 * gateway event got lost, and whenever a role is created, changed or deleted, or the bot's own roles change.
 * Reordering roles fires one event per role, so reports are sent only after things have been quiet for
 * [DEBOUNCE_MILLIS]. A report the backend does not take is sent again with backoff, until a newer one replaces it.
 */
class RoleReporter(
    private val guildId: String,
    private val backend: BackendClient,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private var pending: Job? = null

    fun report(guild: Guild) {
        if (guild.id == guildId) report { guild.roles.map(::toDto) }
    }

    /** Reports [guild] now and then every hour. */
    fun reportPeriodically(guild: Guild) {
        scope.launch {
            while (true) {
                report(guild)
                delay(PERIOD_MILLIS)
            }
        }
    }

    /** Takes the roles only right before each attempt, so a retry sends the current ones. */
    @Synchronized
    internal fun report(roles: () -> List<DiscordRoleDto>) {
        pending?.cancel()
        pending =
            scope.launch {
                delay(DEBOUNCE_MILLIS)
                var retryDelay = MIN_RETRY_MILLIS
                while (true) {
                    try {
                        backend.reportRoles(roles())
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log.warn("Reporting the roles to the backend failed, trying again in {} ms", retryDelay, e)
                    }
                    delay(retryDelay)
                    retryDelay = minOf(retryDelay * 2, MAX_RETRY_MILLIS)
                }
            }
    }

    override fun onRoleCreate(event: RoleCreateEvent) = report(event.guild)

    override fun onRoleDelete(event: RoleDeleteEvent) = report(event.guild)

    override fun onGenericRoleUpdate(event: GenericRoleUpdateEvent<*>) = report(event.guild)

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) = reportIfSelf(event)

    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) = reportIfSelf(event)

    /** The bot's highest role decides which roles it can give. */
    private fun reportIfSelf(event: GenericGuildMemberEvent) {
        if (event.member == event.guild.selfMember) report(event.guild)
    }

    private fun toDto(role: Role): DiscordRoleDto {
        val color = role.colors.primaryRaw
        return DiscordRoleDto(
            id = role.id,
            name = role.name,
            color = color.takeIf { it != Role.DEFAULT_COLOR_RAW },
            position = role.position,
            assignable = !role.isPublicRole && !role.isManaged && role.guild.selfMember.canInteract(role),
        )
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 2_000L
        const val MIN_RETRY_MILLIS = 2_000L
        const val MAX_RETRY_MILLIS = 60_000L
        const val PERIOD_MILLIS = 3_600_000L
    }
}
