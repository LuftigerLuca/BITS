package eu.luftiger.bits.backend.profile.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.backend.shared.MemberRateLimit
import eu.luftiger.bits.shared.ProfileRequest
import eu.luftiger.bits.shared.ProfileResponse
import eu.luftiger.bits.warera.WareraApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** A missing link is part of the answer; an unreachable WarEra API is a 503, a member asking too often a 429. */
@RestController
@RequestMapping("/api/profiles")
class ProfileController(
    private val profileService: ProfileService,
    private val rateLimit: MemberRateLimit,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun profile(
        @RequestBody request: ProfileRequest,
    ): ProfileResponse {
        BotInput.discordUserId(request.discordUserId)
        request.requestedBy?.let(BotInput::discordUserId)
        // The member who asked, so that looking up others again and again counts for them; an older bot sends no one.
        rateLimit.check(request.requestedBy ?: request.discordUserId)
        return profileService.profile(request.discordUserId)
    }

    @ExceptionHandler(WareraApiException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun wareraUnavailable(e: WareraApiException) {
        log.warn("WarEra API not reachable", e)
    }
}
