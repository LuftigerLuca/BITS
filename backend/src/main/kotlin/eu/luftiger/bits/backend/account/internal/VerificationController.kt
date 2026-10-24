package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.shared.BotInput
import eu.luftiger.bits.shared.CheckVerificationRequest
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.StartVerificationRequest
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.UnlinkAccountRequest
import eu.luftiger.bits.shared.UnlinkAccountResponse
import eu.luftiger.bits.warera.WareraApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Expected outcomes are part of the response body; only an unreachable WarEra API (503) and malformed input (400) are
 * errors.
 */
@RestController
@RequestMapping("/api/verifications")
class VerificationController(
    private val verificationService: VerificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun start(
        @RequestBody request: StartVerificationRequest,
    ): StartVerificationResponse {
        BotInput.discordUserId(request.discordUserId)
        BotInput.wareraUsername(request.wareraUsername)
        return verificationService.start(request.discordUserId, request.wareraUsername)
    }

    @PostMapping("/check")
    fun check(
        @RequestBody request: CheckVerificationRequest,
    ): CheckVerificationResponse {
        BotInput.discordUserId(request.discordUserId)
        BotInput.code(request.code)
        return verificationService.check(request.discordUserId, request.code)
    }

    @PostMapping("/unlink")
    fun unlink(
        @RequestBody request: UnlinkAccountRequest,
    ): UnlinkAccountResponse {
        BotInput.discordUserId(request.discordUserId)
        return verificationService.unlink(request.discordUserId)
    }

    @ExceptionHandler(WareraApiException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun wareraUnavailable(e: WareraApiException) {
        log.warn("WarEra API not reachable", e)
    }
}
