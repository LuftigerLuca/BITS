package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.bot.backend.VoteBackend
import eu.luftiger.bits.bot.commands.VerifyCommand
import eu.luftiger.bits.bot.commands.bitsUnavailableMessage
import eu.luftiger.bits.bot.language.Languages
import eu.luftiger.bits.shared.CastVoteResponse
import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.shared.VoteChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * The buttons of a vote. Only members with the role may vote, which the bot checks itself; whether they have a linked
 * WarEra account is up to the backend. The answer goes to the member only, and the message shows the new participation
 * shortly after, without any interim result.
 */
class VoteButtons(
    private val backend: VoteBackend,
    private val scope: CoroutineScope,
    private val languages: Languages,
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Participation updates waiting per vote; many clicks in a row end in one edit. */
    private val pendingUpdates = ConcurrentHashMap<Long, Job>()

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val button = VoteMessages.parseButton(event.componentId) ?: return
        event.deferReply(true).queue()
        val member = event.member
        scope.launch {
            val language = languages.of(event)
            val message =
                try {
                    if (member == null || member.roles.none { it.id == button.roleId }) {
                        language.text(
                            "Abstimmen dürfen nur Mitglieder mit der Rolle <@&${button.roleId}>.",
                            "Only members with the role <@&${button.roleId}> may vote.",
                        )
                    } else {
                        castMessage(backend.castVote(button.voteId, member.id, button.choice), button.choice, language).also {
                            updateLater(button.voteId, button.roleId, event.message)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Casting a vote at the backend failed", e)
                    bitsUnavailableMessage(language)
                }
            event.hook.sendMessage(message).queue()
        }
    }

    private fun updateLater(
        voteId: Long,
        roleId: String,
        message: Message,
    ) {
        pendingUpdates.compute(voteId) { _, pending ->
            pending?.cancel()
            scope.launch {
                delay(UPDATE_DELAY_MILLIS)
                pendingUpdates.remove(voteId, coroutineContext[Job])
                try {
                    // Reporting the participation can end the vote by its threshold, so the vote is loaded after it, or
                    // the message would show the buttons again after the end.
                    val participation = backend.participation(voteId, VoteEvents.roleMemberIds(message.guild, roleId))
                    val vote = backend.vote(voteId)
                    message
                        .editMessageEmbeds(VoteMessages.embed(vote, participation))
                        .setComponents(VoteMessages.components(vote))
                        .submit()
                        .await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The next click or the end of the vote updates it again.
                    log.warn("Participation of vote {} could not be updated", voteId, e)
                }
            }
        }
    }

    companion object {
        /** Long enough to merge a burst of clicks, short enough to look live. */
        private const val UPDATE_DELAY_MILLIS = 2_000L

        internal fun castMessage(
            response: CastVoteResponse,
            choice: VoteChoice,
            language: Language,
        ): String {
            val label = VoteMessages.label(choice, language)
            return when (response) {
                is CastVoteResponse.Cast ->
                    when (val previous = response.previous) {
                        null ->
                            language.text(
                                "Deine Stimme ist gespeichert: **$label**. Sehen kann sie nur du; bis zum Ende kannst du sie ändern.",
                                "Your vote is saved: **$label**. Only you can see it; you can change it until the end.",
                            )
                        choice -> language.text("Du hast schon mit **$label** gestimmt.", "You already voted **$label**.")
                        else -> {
                            val before = VoteMessages.label(previous, language)
                            language.text(
                                "Deine Stimme ist geändert: **$label** statt $before.",
                                "Your vote is changed: **$label** instead of $before.",
                            )
                        }
                    }
                CastVoteResponse.NotLinked ->
                    language.text(
                        "Zum Abstimmen brauchst du einen verknüpften WarEra-Account. Verknüpfe ihn mit `/${VerifyCommand.NAME}` und " +
                            "stimme dann ab.",
                        "To vote, you need a linked WarEra account. Link it with `/${VerifyCommand.NAME}`, then vote.",
                    )
                CastVoteResponse.Closed ->
                    language.text(
                        "Die Abstimmung ist beendet, es werden keine Stimmen mehr angenommen.",
                        "The vote has ended, no more votes are accepted.",
                    )
            }
        }
    }
}
