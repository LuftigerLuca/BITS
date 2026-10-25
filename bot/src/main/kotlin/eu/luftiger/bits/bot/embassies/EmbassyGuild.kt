package eu.luftiger.bits.bot.embassies

/**
 * What the [EmbassySyncHandler] needs from the guild, so it can be tested without JDA; [JdaEmbassyGuild] is the real
 * one. Calls that go to Discord suspend and throw if Discord refuses them.
 */
interface EmbassyGuild {
    val selfId: String

    fun member(discordUserId: String): EmbassyMember?

    fun members(): List<EmbassyMember>

    /** `null` if the role is gone. */
    fun role(roleId: String): EmbassyRole?

    suspend fun thread(
        threadId: String,
        parentChannelId: String,
    ): ThreadLookup

    suspend fun grantRole(
        discordUserId: String,
        roleId: String,
        reason: String,
    )

    suspend fun removeRole(
        discordUserId: String,
        roleId: String,
        reason: String,
    )
}

data class EmbassyMember(
    val id: String,
    val name: String,
    val roleIds: Set<String>,
)

data class EmbassyRole(
    val id: String,
    val name: String,
    /** Whether the bot may give and take it. */
    val assignable: Boolean,
)

sealed interface ThreadLookup {
    data class Found(
        val thread: EmbassyThreadChannel,
    ) : ThreadLookup

    /** Deleted, or the bot cannot see it. */
    data object Missing : ThreadLookup

    /** The bot may not manage threads in the channel, so it cannot even find an archived private thread. */
    data object NoPermission : ThreadLookup
}

interface EmbassyThreadChannel {
    val name: String
    val archived: Boolean

    /** Whether the bot may add and remove members: manage threads and send messages in threads. */
    val manageable: Boolean

    /** Whether the member may manage threads in the thread's channel; BITS leaves such members alone. */
    fun mayManageThreads(discordUserId: String): Boolean

    suspend fun memberIds(): Set<String>

    suspend fun unarchive(reason: String)

    /** @throws MemberCannotViewException if Discord refuses since the member may not see the thread's channel */
    suspend fun add(discordUserId: String)

    suspend fun remove(discordUserId: String)
}

/** Discord adds only members who may see the channel of a private thread. */
class MemberCannotViewException(
    val channelName: String,
    cause: Throwable,
) : Exception("Member may not see #$channelName", cause)
