package eu.luftiger.bits.backend.overview.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.shared.WebInput
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Key figures of the start page and the country they count. Only for admins, see the security config of
 * `/web/admin/`. An unreachable WarEra API is part of the answer, so the Discord figures still show.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/overview")
class OverviewController(
    private val overviewService: OverviewService,
    private val historyService: OverviewHistoryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun overview(): OverviewResponse = overviewService.overview()

    /** The last [days] days, 30 on the start page by default, a year at most. */
    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "30") days: Int,
    ): OverviewHistoryResponse {
        if (days !in 1..OverviewHistoryService.MAX_DAYS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and ${OverviewHistoryService.MAX_DAYS}")
        }
        return historyService.history(days)
    }

    @PutMapping("/country")
    fun setCountry(
        @Valid @RequestBody request: SetCountryRequest,
        @AuthenticationPrincipal session: Jwt,
    ): CountryResponse {
        val actor = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
        val country = overviewService.setCountry(request.countryId, actor)
        log.info("Country of the overview set to {} by {}", country.id, session.subject)
        return country
    }

    /** Only when setting the country; the overview itself shows the citizens as unavailable instead. */
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
data class OverviewHistoryResponse(
    /** First day of the range, ISO date in Europe/Berlin. */
    val from: String,
    /** Last day of the range, today. */
    val to: String,
    /** The days with figures, oldest first; a day that is missing or a `null` figure is a gap, not 0. */
    val days: List<OverviewHistoryDay>,
)

@Serializable
data class OverviewHistoryDay(
    /** ISO date. */
    val day: String,
    val members: Int?,
    val verifiedMembers: Int?,
    /** Of the country set now; `null` on days that counted another one. */
    val citizens: Int?,
    val activeCitizens: Int?,
)

@Serializable
data class SetCountryRequest(
    @field:Size(max = WebInput.MAX_WARERA_ID_LENGTH)
    val countryId: String,
)

@Serializable
data class CountryResponse(
    val id: String,
    val name: String,
)
