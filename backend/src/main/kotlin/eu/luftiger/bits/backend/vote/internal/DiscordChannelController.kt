package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.DiscordChannelsReport
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The bot reports the text channels of the guild on start and whenever one is created, changed or deleted. */
@RestController
@RequestMapping("/api/discord-channels")
class DiscordChannelController(
    private val channelService: DiscordChannelService,
) {
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun replace(
        @RequestBody report: DiscordChannelsReport,
    ) {
        BotInput.channelCount(report.channels.size)
        report.channels.forEach {
            BotInput.channelId(it.id)
            BotInput.channelName(it.name)
            it.category?.let(BotInput::channelName)
            BotInput.roleCount(it.viewerRoleIds.size)
            it.viewerRoleIds.forEach(BotInput::roleId)
        }
        channelService.replaceAll(report.channels)
    }
}
