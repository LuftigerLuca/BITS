package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import jakarta.servlet.http.HttpServletRequest
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Admins only: background and party colours of the result image, see [VoteResultSettingsService]. The background is
 * uploaded as the raw body with its content type, not as a form, so neither side needs multipart.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/votes/result-settings")
class VoteResultSettingsController(
    private val settingsService: VoteResultSettingsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun settings(): ResultSettingsResponse =
        settingsService.settings().let { ResultSettingsResponse(it.customBackground, it.updatedAt?.toEpochMilli()) }

    /** 413 above [MAX_UPLOAD_BYTES], 415 for other types than PNG and JPEG, 400 if it is no image BITS can read. */
    @PutMapping("/background", consumes = [MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setBackground(
        request: HttpServletRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        if (request.contentLengthLong > MAX_UPLOAD_BYTES) throw tooLarge()
        // Read with a limit, since the length may be missing or wrong.
        val upload = request.inputStream.readNBytes(MAX_UPLOAD_BYTES + 1)
        if (upload.size > MAX_UPLOAD_BYTES) throw tooLarge()
        settingsService.setBackground(upload, actor(session))
        log.info("Background of the result image changed by {}", session.subject)
    }

    @DeleteMapping("/background")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetBackground(
        @AuthenticationPrincipal session: Jwt,
    ) {
        settingsService.resetBackground(actor(session))
        log.info("Background of the result image reset by {}", session.subject)
    }

    /** 503 if WarEra cannot be reached. */
    @GetMapping("/party-colors")
    fun partyColors(): PartyColorsResponse =
        settingsService.partyColors().let { colors ->
            PartyColorsResponse(
                countryName = colors.countryName,
                parties = colors.parties.map { PartyColorResponse(it.id, it.name, it.color?.let(::hex)) },
                noPartyColor = colors.noPartyColor?.let(::hex),
            )
        }

    /** 409 if no country is set, 400 for parties not in it or malformed colours, 503 if WarEra cannot be reached. */
    @PutMapping("/party-colors")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setPartyColors(
        @RequestBody request: SetPartyColorsRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        settingsService.setPartyColors(request.colors.mapValues { parse(it.value) }, request.noPartyColor?.let(::parse), actor(session))
        log.info("Party colours of the result image changed by {}", session.subject)
    }

    /** Not cached, since it changes with every setting. */
    @GetMapping("/preview.png", produces = [MediaType.IMAGE_PNG_VALUE])
    fun preview(): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.IMAGE_PNG)
            .body(settingsService.preview())

    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }

    private fun tooLarge() = ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Larger than $MAX_UPLOAD_BYTES bytes")

    private companion object {
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
        val COLOR = Regex("#[0-9a-fA-F]{6}")

        fun parse(color: String): Int {
            if (!COLOR.matches(color)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed colour $color")
            return color.substring(1).toInt(16)
        }

        fun hex(color: Int) = "#%06X".format(color)
    }
}

@Serializable
data class ResultSettingsResponse(
    /** Whether an admin uploaded a background; if not, it is the eagle. */
    val customBackground: Boolean,
    /** Epoch milliseconds of the last change; `null` while nothing was changed. */
    val updatedAt: Long?,
)

@Serializable
data class PartyColorsResponse(
    /** `null` while no country is set under "Verwaltung"; [parties] is empty then. */
    val countryName: String?,
    /** Largest first. */
    val parties: List<PartyColorResponse>,
    /** `#RRGGBB`, `null` for the palette's grey. */
    val noPartyColor: String?,
)

@Serializable
data class PartyColorResponse(
    /** WarEra's ID. */
    val id: String,
    val name: String,
    /** `#RRGGBB`, `null` for the palette's, which depends on the party's place in each vote. */
    val color: String?,
)

@Serializable
data class SetPartyColorsRequest(
    /** `#RRGGBB` by WarEra party ID; parties left out get the palette's again. */
    val colors: Map<String, String>,
    val noPartyColor: String?,
)
