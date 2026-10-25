package eu.luftiger.bits.backend.overview.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.warera.WareraApiException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Key figures of the start page and the country they count. Only for admins, see the security config of
 * `/web/admin/`. An unreachable WarEra API is part of the answer, so the Discord figures still show.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/overview")
class OverviewController(
    private val overviewService: OverviewService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun overview(): OverviewResponse = overviewService.overview()

    @PutMapping("/country")
    fun setCountry(
        @RequestBody request: SetCountryRequest,
        @AuthenticationPrincipal session: Jwt,
    ): CountryResponse {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        val country = overviewService.setCountry(request.countryId, actor)
        log.info("Country of the overview set to {} by {}", country.id, session.subject)
        return country
    }

    /** Only when setting the country; the overview itself shows the citizens as unavailable instead. */
    @ExceptionHandler(WareraApiException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun wareraUnavailable(e: WareraApiException) {
        log.warn("WarEra API not reachable", e)
    }
}

@Serializable
data class OverviewResponse(
    /** Members of the guild without bots; `null` while the bot has not reported them since the backend started. */
    val members: Int?,
    /** Of [members], those with a linked WarEra account; `null` like [members]. */
    val verifiedMembers: Int?,
    /** `null` while no country is set. */
    val country: CountryOverview?,
)

@Serializable
data class CountryOverview(
    val id: String,
    val name: String,
    /** Whether WarEra could be reached; if not, the counts are `null`. */
    val available: Boolean,
    val citizens: Int?,
    val activeCitizens: Int?,
)

@Serializable
data class SetCountryRequest(
    val countryId: String,
)

@Serializable
data class CountryResponse(
    val id: String,
    val name: String,
)
