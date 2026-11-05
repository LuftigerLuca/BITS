package eu.luftiger.bits.backend.externalapi.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.externalapi.domain.ApiToken
import eu.luftiger.bits.backend.shared.InvalidInputException
import eu.luftiger.bits.backend.shared.NotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.HexFormat

/**
 * The tokens of the third-party API: created and revoked by admins under "API-Tokens", checked on every request to
 * `/external/v1/`. A token is a random secret shown once; only its SHA-256 hash is stored, so a leak of the database
 * reveals no usable token. It always expires, at the latest [MAX_DAYS] days after it was created, and cannot be
 * extended or given other scopes: for that, an admin creates a new one.
 */
@Service
class ApiTokenService(
    private val repository: ApiTokenRepository,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun tokens(): ApiTokensResponse =
        ApiTokensResponse(
            tokens = repository.findAllByOrderByCreatedAtDesc().map { it.toResponse() },
            scopes = ExternalScope.entries.map { ScopeResponse(it.id, it.path, it.summary) },
        )

    /** @throws InvalidInputException for an empty name, an unknown scope or an expiry outside of the allowed days */
    fun create(
        request: CreateApiTokenRequest,
        actor: AuditActor,
    ): CreatedApiTokenResponse {
        val name = request.name.trim()
        if (name.isEmpty()) throw InvalidInputException("Name is missing")
        val scopes = request.scopes.distinct().map { ExternalScope.byId(it) ?: throw InvalidInputException("Unknown scope: $it") }
        if (scopes.isEmpty()) throw InvalidInputException("At least one scope is needed")
        val expiresOn =
            try {
                LocalDate.parse(request.expiresOn)
            } catch (_: DateTimeParseException) {
                throw InvalidInputException("expiresOn must be a date like 2026-12-31")
            }
        val today = today()
        if (expiresOn <= today || expiresOn > today.plusDays(MAX_DAYS)) {
            throw InvalidInputException("expiresOn must be between tomorrow and $MAX_DAYS days from today")
        }

        val secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(SECRET_BYTES).also(random::nextBytes))
        val token =
            ApiToken(
                name = name,
                tokenHash = hash(secret),
                tokenHint = secret.take(PREFIX.length + HINT_LENGTH),
                scopes = scopes.mapTo(LinkedHashSet()) { it.id },
                createdAt = clock.instant(),
                createdByName = actor.name,
                // Valid through the whole of the day it expires on.
                expiresAt = expiresOn.plusDays(1).atStartOfDay(ZONE).toInstant(),
            )
        val saved =
            requireNotNull(
                transactionTemplate.execute {
                    repository.save(token).also {
                        auditLog.record(
                            actor,
                            AuditAction.API_TOKEN_CREATED,
                            "„$name“, gültig bis ${DATE.format(expiresOn)}, Scopes: ${scopes.joinToString { it.id }}",
                        )
                    }
                },
            )
        return CreatedApiTokenResponse(saved.toResponse(), secret)
    }

    /** Revoking deletes the token; it cannot be used from now on. @throws NotFoundException if there is no such token */
    fun revoke(
        id: Long,
        actor: AuditActor,
    ): ApiTokenResponse =
        requireNotNull(
            transactionTemplate.execute {
                val token = repository.findByIdOrNull(id) ?: throw NotFoundException()
                repository.delete(token)
                val expired = !token.expiresAt.isAfter(clock.instant())
                auditLog.record(actor, AuditAction.API_TOKEN_REVOKED, "„${token.name}“" + if (expired) " (abgelaufen)" else "")
                token.toResponse()
            },
        )

    /**
     * The token of a request to the third-party API, `null` if [secret] belongs to no token or to an expired one.
     * Records the use, at most once a minute.
     */
    fun authenticate(secret: String): AuthenticatedToken? {
        if (!secret.startsWith(PREFIX) || secret.length > MAX_SECRET_LENGTH) return null
        val token = repository.findByTokenHash(hash(secret)) ?: return null
        val now = clock.instant()
        if (!token.expiresAt.isAfter(now)) return null
        val id = requireNotNull(token.id)
        transactionTemplate.executeWithoutResult { repository.touch(id, now, now.minus(TOUCH_INTERVAL)) }
        return AuthenticatedToken(id, token.scopes.mapNotNullTo(HashSet()) { ExternalScope.byId(it) })
    }

    private fun ApiToken.toResponse(): ApiTokenResponse {
        val now = clock.instant()
        return ApiTokenResponse(
            id = requireNotNull(id),
            name = name,
            hint = tokenHint,
            scopes = scopes.sorted(),
            createdAt = createdAt.toEpochMilli(),
            createdBy = createdByName,
            expiresAt = expiresAt.toEpochMilli(),
            lastUsedAt = lastUsedAt?.toEpochMilli(),
            status =
                when {
                    !expiresAt.isAfter(now) -> ApiTokenStatus.EXPIRED
                    expiresAt.isBefore(now.plus(EXPIRING_SOON)) -> ApiTokenStatus.EXPIRING
                    else -> ApiTokenStatus.ACTIVE
                },
        )
    }

    private fun today(): LocalDate = clock.instant().atZone(ZONE).toLocalDate()

    companion object {
        /** Makes tokens recognisable, e.g. for secret scanners. */
        const val PREFIX = "bits_"

        /** 256 bits. */
        private const val SECRET_BYTES = 32

        /** Base64 of [SECRET_BYTES] without padding, and the prefix. */
        private const val MAX_SECRET_LENGTH = 64

        private const val HINT_LENGTH = 4

        /** The longest a token may be valid. */
        const val MAX_DAYS = 365L

        const val MAX_NAME_LENGTH = 100

        /** The dashboard marks a token as expiring this long before. */
        private val EXPIRING_SOON: Duration = Duration.ofDays(14)

        private val TOUCH_INTERVAL: Duration = Duration.ofMinutes(1)

        /** The day of the German Discord. */
        private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        fun hash(secret: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()))
    }
}

/** A token that passed [ApiTokenService.authenticate]. */
data class AuthenticatedToken(
    val id: Long,
    val scopes: Set<ExternalScope>,
)
