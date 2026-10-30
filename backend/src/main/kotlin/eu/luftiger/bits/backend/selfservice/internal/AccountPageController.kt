package eu.luftiger.bits.backend.selfservice.internal

import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.shared.MemberRateLimit
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * The page "Mein Account", where every member sees and deletes what BITS keeps about them. The Discord ID always comes
 * from the session, never from the request, so nobody reaches the data of somebody else. The admin account from the
 * environment has no Discord ID and gets 403.
 */
@RestController
@RequestMapping("/web/account")
class AccountPageController(
    private val service: AccountPageService,
    private val rateLimit: MemberRateLimit,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun account(
        @AuthenticationPrincipal jwt: Jwt,
    ): AccountResponse = service.account(discordUserId(jwt))

    /**
     * Judged with fresh WarEra data like `/rollen`, so it can take a while; 503 if WarEra cannot be reached, 429 if the
     * member asked too often, see [MemberRateLimit].
     */
    @GetMapping("/roles")
    fun roles(
        @AuthenticationPrincipal jwt: Jwt,
    ): AccountRolesResponse {
        val discordUserId = discordUserId(jwt)
        rateLimit.check(discordUserId)
        return service.roles(discordUserId)
    }

    /** Works like `/unverify`; 404 if there is no link. */
    @DeleteMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlink(
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        if (!service.unlink(discordUserId(jwt))) throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    /** Everything BITS keeps about the member, as a JSON file to save. */
    @GetMapping("/export")
    fun export(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<String> {
        val discordUserId = discordUserId(jwt)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename("bits-daten-$discordUserId.json")
                    .build()
                    .toString(),
            ).body(service.export(discordUserId))
    }

    private fun discordUserId(jwt: Jwt): String =
        SessionPrincipal.of(jwt).discordUserId ?: throw ResponseStatusException(HttpStatus.FORBIDDEN)
}

@Serializable
data class AccountResponse(
    /** `null` if the member has not linked a WarEra account. */
    val link: LinkResponse?,
    /** Epoch milliseconds of the last sync of all members the bot applied; `null` if none is known. */
    val lastSyncAt: Long?,
)

@Serializable
data class LinkResponse(
    /** Name at the time of linking. */
    val wareraUsername: String,
    /** Epoch milliseconds. */
    val verifiedAt: Long,
)

@Serializable
data class AccountRolesResponse(
    val linked: Boolean,
    /** Every role BITS manages, highest in the guild first; empty if the member is not linked or there are no rules. */
    val roles: List<AccountRoleResponse>,
)

@Serializable
data class AccountRoleResponse(
    val id: String,
    val name: String,
    val color: Int?,
    val outcome: RoleOutcomeResponse,
    /** The rules of the role, any one of which is enough. */
    val rules: List<AccountRuleResponse>,
)

@Serializable
enum class RoleOutcomeResponse {
    MET,
    NOT_MET,

    /** No rule is met and the WarEra data for at least one is missing; the member keeps the role as it is. */
    UNDECIDED,
}

@Serializable
data class AccountRuleResponse(
    /** German description of the rule. */
    val label: String,
    /** `null` if the WarEra data to decide is missing. */
    val met: Boolean?,
    /** German description of what is missing; `null` if the rule is met. */
    val detail: String?,
)
