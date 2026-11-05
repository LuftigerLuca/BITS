package eu.luftiger.bits.backend.externalapi.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The page "API-Tokens" of the dashboard. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/api-tokens")
class ApiTokenController(
    private val service: ApiTokenService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun tokens(): ApiTokensResponse = service.tokens()

    /** The answer holds the token itself, which is never shown again. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateApiTokenRequest,
        @AuthenticationPrincipal session: Jwt,
    ): CreatedApiTokenResponse {
        val created = service.create(request, actor(session))
        log.info("API token {} created by {}", created.token.id, session.subject)
        return created
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @PathVariable id: Long,
        @AuthenticationPrincipal session: Jwt,
    ) {
        service.revoke(id, actor(session))
        log.info("API token {} revoked by {}", id, session.subject)
    }

    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
}

@Serializable
data class ApiTokensResponse(
    /** The newest first, expired ones included until they are removed. */
    val tokens: List<ApiTokenResponse>,
    /** All scopes a token can get, in the order of the documentation. */
    val scopes: List<ScopeResponse>,
)

@Serializable
data class ApiTokenResponse(
    val id: Long,
    val name: String,
    /** The start of the token, e.g. `bits_AbCd`. */
    val hint: String,
    val scopes: List<String>,
    val createdAt: Long,
    val createdBy: String,
    /** The first moment the token is no longer valid: midnight after the day it expires on. */
    val expiresAt: Long,
    /** To the minute; `null` if the token was never used. */
    val lastUsedAt: Long?,
    val status: ApiTokenStatus,
)

@Serializable
enum class ApiTokenStatus {
    ACTIVE,

    /** Expires within two weeks. */
    EXPIRING,
    EXPIRED,
}

@Serializable
data class ScopeResponse(
    val id: String,
    /** The endpoint the scope allows, e.g. `/external/v1/accounts`; always with GET. */
    val path: String,
    /** German. */
    val summary: String,
)

@Serializable
data class CreateApiTokenRequest(
    @field:Size(max = ApiTokenService.MAX_NAME_LENGTH)
    val name: String,
    /** The last day the token is valid, e.g. `2026-12-31`; at most [ApiTokenService.MAX_DAYS] days from today. */
    @field:Size(max = 10)
    val expiresOn: String,
    @field:Size(max = MAX_SCOPES)
    val scopes: List<
        @Size(max = MAX_SCOPE_LENGTH)
        String,
    >,
)

/** The token and the secret itself, which only this answer contains. */
@Serializable
data class CreatedApiTokenResponse(
    val token: ApiTokenResponse,
    val secret: String,
)

/** More than there are scopes; duplicates are ignored. */
private const val MAX_SCOPES = 50

private const val MAX_SCOPE_LENGTH = 64
