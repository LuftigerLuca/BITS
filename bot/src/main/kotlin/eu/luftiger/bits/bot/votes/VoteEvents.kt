package eu.luftiger.bits.bot.votes

import eu.luftiger.bits.bot.backend.VoteBackend
import eu.luftiger.bits.shared.Elector
import eu.luftiger.bits.shared.ParticipationResponse
import eu.luftiger.bits.shared.VoteClosedEvent
import eu.luftiger.bits.shared.VoteDto
import eu.luftiger.bits.shared.VoteEvaluatedEvent
import eu.luftiger.bits.shared.VoteStartedEvent
import eu.luftiger.bits.shared.VoteState
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.utils.FileUpload
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Carries out what the backend decided about a vote: posts it with the buttons, closes the message at the end and
 * reports the members of the role, and posts the result. The handlers look up the vote's current state first, so an
 * event that comes again, or late, does no harm; only a result whose posting is not confirmed to the backend is posted
 * again.
 */
class VoteEvents(
    private val jda: JDA,
    private val guildId: String,
    private val backend: VoteBackend,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun onStarted(event: VoteStartedEvent) {
        val vote = backend.vote(event.voteId)
        if (vote.state != VoteState.OPEN || vote.messageId != null) return
        val guild = guild()
        val channel = channel(guild, vote)
        if (channel == null) {
            log.warn("Channel {} of vote {} is gone, so the vote is not posted", vote.channelId, vote.id)
            return
        }
        val participation = backend.participation(vote.id, roleMemberIds(guild, vote.roleId))
        val message =
            channel
                .sendMessage("<@&${vote.roleId}>")
                .setEmbeds(VoteMessages.embed(vote, participation))
                .setComponents(VoteMessages.components(vote))
                .setAllowedMentions(listOf(Message.MentionType.ROLE))
                .mentionRoles(vote.roleId)
                .submit()
                .await()
        backend.reportVoteMessage(vote.id, message.id)
        log.info("Vote {} posted in channel {}", vote.id, vote.channelId)
    }

    suspend fun onClosed(event: VoteClosedEvent) {
        val vote = backend.vote(event.voteId)
        val guild = guild()
        val counting = vote.state == VoteState.COUNTING
        val participation = if (counting) backend.participation(vote.id, roleMemberIds(guild, vote.roleId)) else null
        editMessage(guild, vote, participation)
        if (counting) {
            val electors = roleMembers(guild, vote.roleId).map { Elector(it.id, it.effectiveName) }
            backend.reportElectorate(vote.id, electors)
            log.info("Vote {} closed, {} role members reported", vote.id, electors.size)
        }
    }

    suspend fun onEvaluated(event: VoteEvaluatedEvent) {
        val result = backend.voteResult(event.voteId)
        if (result.posted) return
        val vote = backend.vote(event.voteId)
        val channel = channel(guild(), vote)
        if (channel == null) {
            // Asking again would not bring the channel back; the result is in the dashboard.
            log.warn("Channel {} of vote {} is gone, so the result is not posted", vote.channelId, vote.id)
            backend.markVoteResultPosted(vote.id)
            return
        }
        val parts = VoteMessages.split(result.text)
        val image = FileUpload.fromData(Base64.getDecoder().decode(result.image), "abstimmung-${vote.id}.png")
        channel
            .sendMessage(parts.first())
            .addFiles(image)
            .setAllowedMentions(emptyList())
            .apply { vote.messageId?.let { setMessageReference(it).failOnInvalidReply(false) } }
            .submit()
            .await()
        parts.drop(1).forEach {
            channel
                .sendMessage(it)
                .setAllowedMentions(emptyList())
                .submit()
                .await()
        }
        backend.markVoteResultPosted(vote.id)
        log.info("Result of vote {} posted in channel {}", vote.id, vote.channelId)
    }

    /** Leaves a message that is gone alone; the vote goes on without it. */
    private suspend fun editMessage(
        guild: Guild,
        vote: VoteDto,
        participation: ParticipationResponse?,
    ) {
        val messageId = vote.messageId ?: return
        val channel = channel(guild, vote) ?: return
        try {
            channel
                .editMessageEmbedsById(messageId, VoteMessages.embed(vote, participation))
                .setComponents(VoteMessages.components(vote))
                .submit()
                .await()
        } catch (e: ErrorResponseException) {
            if (e.errorResponse != ErrorResponse.UNKNOWN_MESSAGE) throw e
            log.info("Message of vote {} was deleted, not updating it", vote.id)
        }
    }

    private fun channel(
        guild: Guild,
        vote: VoteDto,
    ): GuildMessageChannel? = guild.getGuildChannelById(vote.channelId) as? GuildMessageChannel

    private fun guild(): Guild = jda.getGuildById(guildId) ?: error("Bot is not a member of guild $guildId")

    companion object {
        /** Members of the role without bots; none if the role is gone. */
        fun roleMembers(
            guild: Guild,
            roleId: String,
        ): List<Member> {
            val role = guild.getRoleById(roleId) ?: return emptyList()
            return guild.getMembersWithRoles(role).filterNot { it.user.isBot }
        }

        fun roleMemberIds(
            guild: Guild,
            roleId: String,
        ): List<String> = roleMembers(guild, roleId).map { it.id }
    }
}
