package eu.luftiger.bits.bot.accounts

import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA

fun interface DirectMessages {
    /** Fails if the user cannot be found or does not accept direct messages from the bot. */
    suspend fun send(
        discordUserId: String,
        text: String,
    )
}

class JdaDirectMessages(
    private val jda: JDA,
) : DirectMessages {
    override suspend fun send(
        discordUserId: String,
        text: String,
    ) {
        jda
            .retrieveUserById(discordUserId)
            .flatMap { it.openPrivateChannel() }
            .flatMap { it.sendMessage(text) }
            .submit()
            .await()
    }
}
