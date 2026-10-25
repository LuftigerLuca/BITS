package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.NicknamePreferenceRequest
import eu.luftiger.bits.shared.NicknamePreferenceResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** `/nickname` and the button after linking; behind the service token like everything under `/api`. */
@RestController
@RequestMapping("/api/nickname-preferences")
class NicknamePreferenceController(
    private val preferenceService: NicknamePreferenceService,
) {
    @PostMapping
    fun set(
        @RequestBody request: NicknamePreferenceRequest,
    ): NicknamePreferenceResponse {
        BotInput.discordUserId(request.discordUserId)
        return preferenceService.set(request.discordUserId, request.enabled)
    }
}
