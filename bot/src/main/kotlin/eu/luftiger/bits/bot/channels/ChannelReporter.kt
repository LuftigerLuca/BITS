package eu.luftiger.bits.bot.channels

import eu.luftiger.bits.bot.backend.BackendClient
import eu.luftiger.bits.bot.backend.DebouncedReport
import eu.luftiger.bits.shared.DiscordChannelDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel
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
 * Tells the backend which text channels the guild has and in which of them the bot may post votes: on start, every
 * hour in case a gateway event got lost, and whenever a channel, its permissions or the bot's roles change. Reports go
 * through a [DebouncedReport].
 */
class ChannelReporter(
    private val guildId: String,
    backend: BackendClient,
    private val scope: CoroutineScope,
) : ListenerAdapter() {
    private val reports = DebouncedReport(scope, "the channels", backend::reportChannels)

    fun report(guild: Guild) {
        if (guild.id == guildId) reports.report { channels(guild) }
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

    override fun onGenericChannelUpdate(event: GenericChannelUpdateEvent<*>) = report(event.guild)

    override fun onGenericPermissionOverride(event: GenericPermissionOverrideEvent) = report(event.guild)

    /** The permissions of a role decide where the bot may post. */
    override fun onGenericRoleUpdate(event: GenericRoleUpdateEvent<*>) = report(event.guild)

    override fun onGuildMemberRoleAdd(event: GuildMemberRoleAddEvent) = reportIfSelf(event)

    override fun onGuildMemberRoleRemove(event: GuildMemberRoleRemoveEvent) = reportIfSelf(event)

    private fun reportIfSelf(event: GenericGuildMemberEvent) {
        if (event.member == event.guild.selfMember) report(event.guild)
    }

    companion object {
        private const val PERIOD_MILLIS = 3_600_000L

        /** What the bot needs to post a vote with buttons, edit it, and reply to it with the result image. */
        val POST_PERMISSIONS =
            listOf(
                Permission.VIEW_CHANNEL,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_HISTORY,
            )

        /** Text and announcement channels in the order of Discord's channel list. */
        internal fun channels(guild: Guild): List<DiscordChannelDto> =
            guild.channels.filterIsInstance<StandardGuildMessageChannel>().mapIndexed { index, channel ->
                DiscordChannelDto(
                    id = channel.id,
                    name = channel.name,
                    category = channel.parentCategory?.name,
                    position = index,
                    postable = guild.selfMember.hasPermission(channel, POST_PERMISSIONS),
                )
            }
    }
}
