package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.LanguagePreferenceRequest
import eu.luftiger.bits.shared.LanguagePreferenceResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** `/language` and the lookups before the bot answers; behind the service token like everything under `/api`. */
@RestController
@RequestMapping("/api/language-preferences")
class LanguagePreferenceController(
    private val preferenceService: LanguagePreferenceService,
) {
    @GetMapping("/{discordUserId}")
    fun get(
        @PathVariable discordUserId: String,
    ): LanguagePreferenceResponse {
        BotInput.discordUserId(discordUserId)
        return LanguagePreferenceResponse(preferenceService.get(discordUserId))
    }

    @PostMapping
    fun set(
        @RequestBody request: LanguagePreferenceRequest,
    ): LanguagePreferenceResponse {
        BotInput.discordUserId(request.discordUserId)
        preferenceService.set(request.discordUserId, request.language)
        return LanguagePreferenceResponse(request.language)
    }
}
