package eu.luftiger.bits.bot.embassies

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel

/** [EmbassyGuild] on the guild [guildId], whose members JDA keeps in its cache. */
class JdaEmbassyGuild(
    private val jda: JDA,
    private val guildId: String,
) : EmbassyGuild {
    private val guild: Guild
        get() = jda.getGuildById(guildId) ?: error("Bot is not a member of guild $guildId")

    override val selfId: String
        get() = jda.selfUser.id

    override fun member(discordUserId: String): EmbassyMember? = guild.getMemberById(discordUserId)?.let(::toMember)

    override fun members(): List<EmbassyMember> = guild.members.filterNot { it.user.isBot }.map(::toMember)

    override fun role(roleId: String): EmbassyRole? =
        guild.getRoleById(roleId)?.let { role ->
            EmbassyRole(role.id, role.name, assignable = !role.isPublicRole && !role.isManaged && guild.selfMember.canInteract(role))
        }

    /** An archived thread is not in the cache, so it is looked up among the archived private threads of its channel. */
    override suspend fun thread(
        threadId: String,
        parentChannelId: String,
    ): ThreadLookup {
        guild.getThreadChannelById(threadId)?.let { return ThreadLookup.Found(JdaThread(it)) }
        val parent = guild.getTextChannelById(parentChannelId) ?: return ThreadLookup.Missing
        if (!guild.selfMember.hasPermission(parent, Permission.MANAGE_THREADS, Permission.VIEW_CHANNEL)) return ThreadLookup.NoPermission
        val archived =
            parent
                .retrieveArchivedPrivateThreadChannels()
                .takeRemainingAsync(MAX_THREADS)
                .await()
                .firstOrNull { it.id == threadId }
        return archived?.let { ThreadLookup.Found(JdaThread(it)) } ?: ThreadLookup.Missing
    }

    override suspend fun grantRole(
        discordUserId: String,
        roleId: String,
        reason: String,
    ) {
        val role = guild.getRoleById(roleId) ?: error("Role $roleId is gone")
        guild
            .addRoleToMember(UserSnowflake.fromId(discordUserId), role)
            .reason(reason)
            .submit()
            .await()
    }

    override suspend fun removeRole(
        discordUserId: String,
        roleId: String,
        reason: String,
    ) {
        val role = guild.getRoleById(roleId) ?: error("Role $roleId is gone")
        guild
            .removeRoleFromMember(UserSnowflake.fromId(discordUserId), role)
            .reason(reason)
            .submit()
            .await()
    }

    private fun toMember(member: Member) = EmbassyMember(member.id, member.effectiveName, member.roles.mapTo(HashSet()) { it.id })

    private inner class JdaThread(
        private val thread: ThreadChannel,
    ) : EmbassyThreadChannel {
        private val parent: GuildChannel = thread.parentChannel

        override val name: String = thread.name
        override val archived: Boolean = thread.isArchived
        override val manageable: Boolean =
            guild.selfMember.hasPermission(parent, Permission.VIEW_CHANNEL, Permission.MANAGE_THREADS, Permission.MESSAGE_SEND_IN_THREADS)

        override fun mayManageThreads(discordUserId: String): Boolean =
            guild.getMemberById(discordUserId)?.hasPermission(parent, Permission.MANAGE_THREADS) == true

        override suspend fun memberIds(): Set<String> =
            thread
                .retrieveThreadMembers()
                .takeRemainingAsync(MAX_MEMBERS)
                .await()
                .mapTo(HashSet()) { it.id }

        override suspend fun unarchive(reason: String) {
            thread.manager
                .setArchived(false)
                .reason(reason)
                .submit()
                .await()
        }

        override suspend fun add(discordUserId: String) {
            thread.addThreadMemberById(discordUserId).submit().await()
        }

        override suspend fun remove(discordUserId: String) {
            thread.removeThreadMemberById(discordUserId).submit().await()
        }
    }

    private companion object {
        /** Far more than a channel or a thread has; the paging stops at the end anyway. */
        const val MAX_THREADS = 10_000
        const val MAX_MEMBERS = 10_000
    }
}
