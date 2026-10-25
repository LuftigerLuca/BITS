package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.bot.backend.DebouncedReport
import eu.luftiger.bits.bot.backend.EmbassyBackend
import eu.luftiger.bits.shared.DiscordThreadDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent
import net.dv8tion.jda.api.events.guild.member.GenericGuildMemberEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.guild.override.GenericPermissionOverrideEvent
import net.dv8tion.jda.api.events.role.update.GenericRoleUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Tells the backend which private threads the guild has, active and archived, for picking embassy threads: on start,
 * every hour, and whenever a channel or thread, its permissions or the bot's roles change. Archived private threads
 * are not in JDA's cache, so they are fetched from each text channel the bot may manage threads in. Reports go through
 * a [DebouncedReport].
 */
class ThreadReporter(
    private val guildId: String,
    backend: EmbassyBackend,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val reports = DebouncedReport<Guild>(scope, "the threads") { backend.reportThreads(threads(it)) }

    fun report(guild: Guild) {
        if (guild.id == guildId) reports.report { guild }
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

    override fun onChannelCreate(event: ChannelCreateEvent) = report(event.guild)

    override fun onChannelDelete(event: ChannelDeleteEvent) = report(event.guild)

    /** Also when a thread is archived, unarchived or renamed. */
    override fun onGenericChannelUpdate(event: GenericChannelUpdateEvent<*>) = report(event.guild)

    override fun onGenericPermissionOverride(event: GenericPermissionOverrideEvent) = report(event.guild)

    override fun onGenericRoleUpdate(event: GenericRoleUpdateEvent<*>) = report(event.guild)

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) = reportIfSelf(event)

    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) = reportIfSelf(event)

    private fun reportIfSelf(event: GenericGuildMemberEvent) {
        if (event.member == event.guild.selfMember) report(event.guild)
    }

    companion object {
        private const val PERIOD_MILLIS = 3_600_000L
        private const val MAX_ARCHIVED = 10_000

        internal suspend fun threads(guild: Guild): List<DiscordThreadDto> {
            val active = guild.threadChannels.filter { it.type == ChannelType.GUILD_PRIVATE_THREAD }
            val archived =
                guild.textChannels
                    .filter { guild.selfMember.hasPermission(it, Permission.VIEW_CHANNEL, Permission.MANAGE_THREADS) }
                    .flatMap { it.retrieveArchivedPrivateThreadChannels().takeRemainingAsync(MAX_ARCHIVED).await() }
            return (active + archived).distinctBy { it.id }.map { toDto(guild, it) }
        }

        private fun toDto(
            guild: Guild,
            thread: ThreadChannel,
        ) = DiscordThreadDto(
            id = thread.id,
            name = thread.name,
            parentChannelId = thread.parentChannel.id,
            parentChannelName = thread.parentChannel.name,
            archived = thread.isArchived,
            manageable =
                guild.selfMember.hasPermission(
                    thread.parentChannel,
                    Permission.VIEW_CHANNEL,
                    Permission.MANAGE_THREADS,
                    Permission.MESSAGE_SEND_IN_THREADS,
                ),
        )
    }
}
