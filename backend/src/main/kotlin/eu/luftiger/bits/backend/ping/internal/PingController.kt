package eu.luftiger.bits.backend.ping.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.PingRequest
import eu.luftiger.bits.shared.PingResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ping")
class PingController(
    private val pingService: PingService,
) {
    @PostMapping
    fun ping(
        @RequestBody request: PingRequest,
    ): PingResponse {
        BotInput.discordUserId(request.discordUserId)
        return pingService.ping(request.discordUserId)
    }
}
