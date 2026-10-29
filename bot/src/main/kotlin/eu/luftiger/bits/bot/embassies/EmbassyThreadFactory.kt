package eu.luftiger.bits.bot.embassies

import eu.luftiger.bits.shared.CreatedThread

/**
 * Creates embassy threads for the [EmbassyThreadCreator], so it can be tested without JDA; [JdaEmbassyGuild] is the
 * real one. Calls suspend and throw if Discord refuses them for another reason than the ones in [ThreadCreation].
 */
interface EmbassyThreadFactory {
    /** A private thread that only moderators can invite to and that archives after the longest time Discord allows. */
    suspend fun createPrivateThread(
        channelId: String,
        name: String,
        reason: String,
    ): ThreadCreation

    suspend fun deleteThread(
        threadId: String,
        reason: String,
    )
}

sealed interface ThreadCreation {
    data class Created(
        val thread: CreatedThread,
    ) : ThreadCreation

    /** Deleted, not a text channel, or the bot cannot see it. */
    data object ChannelMissing : ThreadCreation

    /** The bot may not create private threads, manage threads or send messages in threads there. */
    data object NoPermission : ThreadCreation
}
