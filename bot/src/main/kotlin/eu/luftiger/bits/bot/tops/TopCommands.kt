package eu.luftiger.bits.bot.tops

import eu.luftiger.bits.bot.commands.bitsUnavailableMessage
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.bot.votes.VoteCommand
import eu.luftiger.bits.shared.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.DiscordLocale
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.slf4j.LoggerFactory

/**
 * Runs a TOP command in the post it was used in: [run] gets the post, the member and their language and returns the
 * reply, which only the member sees. Outside a forum post the command is refused right away.
 */
abstract class TopCommand(
    private val name: String,
    private val scope: CoroutineScope,
    private val languages: Languages,
    private val post: (SlashCommandInteractionEvent) -> TopPost?,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    protected abstract suspend fun run(
        event: SlashCommandInteractionEvent,
        post: TopPost,
        member: Member,
        language: Language,
    ): String

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        if (event.name != name) return
        event.deferReply(true).queue()
        val member = event.member
        val post = post(event)
        scope.launch {
            val language = languages.of(event)
            val message =
                try {
                    if (member == null || post == null) Tops.notInTopMessage(language) else run(event, post, member, language)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("/{} failed at the backend", name, e)
                    bitsUnavailableMessage(language)
                }
            event.hook.sendMessage(message).queue()
        }
    }
}

/** The forum post the command was used in, `null` anywhere else. */
fun forumPost(event: SlashCommandInteractionEvent): TopPost? {
    val thread = event.channel as? ThreadChannel ?: return null
    val forum = thread.parentChannel as? ForumChannel ?: return null
    return JdaTopPost(thread, forum)
}

/** `/topclose [reason] [archive]` in a TOP post. */
class TopCloseCommand(
    private val tops: Tops,
    scope: CoroutineScope,
    languages: Languages,
    post: (SlashCommandInteractionEvent) -> TopPost? = ::forumPost,
) : TopCommand(NAME, scope, languages, post) {
    override suspend fun run(
        event: SlashCommandInteractionEvent,
        post: TopPost,
        member: Member,
        language: Language,
    ): String =
        tops.close(
            post,
            VoteCommand.actor(member),
            member.asMention,
            event.getOption(OPTION_REASON)?.asString,
            event.getOption(OPTION_ARCHIVE)?.asBoolean ?: false,
            language,
        )

    companion object {
        const val NAME = "topclose"
        private const val OPTION_REASON = "reason"
        private const val OPTION_ARCHIVE = "archive"
        private const val MAX_REASON_LENGTH = 500

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Closes the agenda item of this post: message, done tag, lock")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Schließt den TOP dieses Posts: Nachricht, Erledigt-Tag, Sperre")
                .addOptions(
                    OptionData(OptionType.STRING, OPTION_REASON, "Why it is closed; appears in the closing message", false)
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Warum er geschlossen wird; steht in der Abschlussnachricht")
                        .setMaxLength(MAX_REASON_LENGTH),
                    OptionData(OptionType.BOOLEAN, OPTION_ARCHIVE, "Archive the post as well; default no", false)
                        .setDescriptionLocalization(DiscordLocale.GERMAN, "Den Post auch archivieren; Standard nein"),
                )
    }
}

/** `/topopen` in a closed TOP post. */
class TopOpenCommand(
    private val tops: Tops,
    scope: CoroutineScope,
    languages: Languages,
    post: (SlashCommandInteractionEvent) -> TopPost? = ::forumPost,
) : TopCommand(NAME, scope, languages, post) {
    override suspend fun run(
        event: SlashCommandInteractionEvent,
        post: TopPost,
        member: Member,
        language: Language,
    ): String = tops.reopen(post, VoteCommand.actor(member), member.asMention, language)

    companion object {
        const val NAME = "topopen"

        val data: SlashCommandData =
            Commands
                .slash(NAME, "Reopens the closed agenda item of this post")
                .setDescriptionLocalization(DiscordLocale.GERMAN, "Öffnet den geschlossenen TOP dieses Posts wieder")
    }
}
