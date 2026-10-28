package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.api.GuildChannels
import eu.luftiger.bits.backend.vote.api.dto.GuildChannel
import eu.luftiger.bits.backend.vote.domain.DiscordChannel
import eu.luftiger.bits.shared.DiscordChannelDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiscordChannelService(
    private val channelRepository: DiscordChannelRepository,
) : GuildChannels {
    @Transactional
    fun replaceAll(channels: List<DiscordChannelDto>) {
        channelRepository.deleteAllInBatch()
        channelRepository.saveAll(
            channels.map {
                DiscordChannel(
                    it.id,
                    it.name,
                    it.category,
                    it.position,
                    it.postable,
                    it.threadsCreatable,
                    it.everyoneCanView,
                    it.viewerRoleIds.toSet(),
                )
            },
        )
    }

    /** Channels the bot may post in, in the order Discord lists them. */
    fun postable(): List<DiscordChannel> = channelRepository.findAll().filter { it.postable }.sortedBy { it.position }

    override fun all(): List<GuildChannel> =
        channelRepository.findAll().sortedBy { it.position }.map {
            GuildChannel(it.id, it.name, it.category, it.threadsCreatable, it.everyoneCanView, it.viewerRoleIds)
        }
}
