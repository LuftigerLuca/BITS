package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.vote.domain.DiscordChannel
import eu.luftiger.bits.shared.DiscordChannelDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DiscordChannelService(
    private val channelRepository: DiscordChannelRepository,
) {
    @Transactional
    fun replaceAll(channels: List<DiscordChannelDto>) {
        channelRepository.deleteAllInBatch()
        channelRepository.saveAll(channels.map { DiscordChannel(it.id, it.name, it.category, it.position, it.postable) })
    }

    /** Channels the bot may post in, in the order Discord lists them. */
    fun postable(): List<DiscordChannel> = channelRepository.findAll().filter { it.postable }.sortedBy { it.position }
}
