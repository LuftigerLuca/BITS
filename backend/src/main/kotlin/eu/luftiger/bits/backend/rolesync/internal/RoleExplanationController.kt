package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.backend.shared.MemberRateLimit
import eu.luftiger.bits.shared.RoleExplanationRequest
import eu.luftiger.bits.shared.RoleExplanationResponse
import eu.luftiger.bits.warera.WareraApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** A missing link is part of the answer; a user WarEra cannot deliver is a 503, a member asking too often a 429. */
@RestController
@RequestMapping("/api/role-explanations")
class RoleExplanationController(
    private val explanationService: RoleExplanationService,
    private val rateLimit: MemberRateLimit,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun explain(
        @RequestBody request: RoleExplanationRequest,
    ): RoleExplanationResponse {
        BotInput.discordUserId(request.discordUserId)
        rateLimit.check(request.discordUserId)
        return explanationService.explain(request.discordUserId)
    }

    @ExceptionHandler(WareraApiException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun wareraUnavailable(e: WareraApiException) {
        log.warn("WarEra API not reachable", e)
    }
}
