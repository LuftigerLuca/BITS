package eu.luftiger.bits.bot.tops

import eu.luftiger.bits.bot.channels.ChannelReporter
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.entities.channel.forums.ForumTagSnowflake

/** The forum post a TOP command was used in, as far as [Tops] needs it; every call fails with what JDA throws. */
interface TopPost {
    val id: String
    val name: String
    val forumId: String

    /** A TOP is closed while its post is locked. */
    val locked: Boolean

    /** In the order Discord shows them. */
    val appliedTagIds: List<String>

    /** The tags the forum still has; a done tag deleted in Discord is no longer among them. */
    val forumTagIds: Set<String>

    /** Whether the bot may write in the post, tag, lock and archive it. */
    val botMayManage: Boolean

    suspend fun send(embed: MessageEmbed)

    /** Sets the tags, the lock and the archive state in one request, so nothing has to change after archiving. */
    suspend fun update(
        tagIds: List<String>,
        locked: Boolean,
        archived: Boolean,
    )
}

class JdaTopPost(
    private val thread: ThreadChannel,
    private val forum: ForumChannel,
) : TopPost {
    override val id: String get() = thread.id
    override val name: String get() = thread.name
    override val forumId: String get() = forum.id
    override val locked: Boolean get() = thread.isLocked
    override val appliedTagIds: List<String> get() = thread.appliedTags.map { it.id }
    override val forumTagIds: Set<String> get() = forum.availableTags.mapTo(HashSet()) { it.id }
    override val botMayManage: Boolean get() = forum.guild.selfMember.hasPermission(forum, ChannelReporter.TOP_PERMISSIONS)

    override suspend fun send(embed: MessageEmbed) {
        thread
            .sendMessageEmbeds(embed)
            .submit()
            .await()
    }

    override suspend fun update(
        tagIds: List<String>,
        locked: Boolean,
        archived: Boolean,
    ) {
        thread.manager
            .setAppliedTags(tagIds.map(ForumTagSnowflake::fromId))
            .setLocked(locked)
            .setArchived(archived)
            .submit()
            .await()
    }
}
