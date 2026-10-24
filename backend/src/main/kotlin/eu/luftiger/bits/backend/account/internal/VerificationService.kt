package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import eu.luftiger.bits.backend.account.domain.AccountLink
import eu.luftiger.bits.backend.account.domain.AccountVerification
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraUser
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration

/**
 * Links Discord accounts to WarEra accounts. A user proves ownership of a WarEra account by putting a one-time code
 * into the name of one of its companies, which the backend then finds through the public API.
 *
 * WarEra calls run outside of transactions, since they can take a while when the client is throttled.
 */
@Service
class VerificationService(
    private val linkRepository: AccountLinkRepository,
    private val verificationRepository: AccountVerificationRepository,
    private val wareraClient: WareraClient,
    private val eventPublisher: EventPublisher,
    private val applicationEvents: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun start(
        discordUserId: String,
        wareraUsername: String,
    ): StartVerificationResponse {
        val user = findUser(wareraUsername.trim()) ?: return StartVerificationResponse.UserNotFound
        if (wareraClient.getCompanyIds(user.id).isEmpty()) return StartVerificationResponse.NoCompany(user.username)

        // Saving under the same ID replaces an open code of the user, which invalidates it.
        val verification =
            verificationRepository.save(
                AccountVerification(
                    discordUserId = discordUserId,
                    wareraUserId = user.id,
                    wareraUsername = user.username,
                    code = generateCode(),
                    expiresAt = clock.instant() + CODE_VALIDITY,
                ),
            )
        return StartVerificationResponse.Started(
            code = verification.code,
            wareraUsername = user.username,
            expiresAt = verification.expiresAt.toEpochMilli(),
            linkedWareraUsername = linkRepository.findByIdOrNull(discordUserId)?.wareraUsername,
        )
    }

    fun check(
        discordUserId: String,
        code: String,
    ): CheckVerificationResponse {
        val now = clock.instant()
        val verification = verificationRepository.findByIdOrNull(discordUserId)
        if (verification == null || !verification.code.equals(code.trim(), ignoreCase = true)) {
            return CheckVerificationResponse.Invalid
        }
        if (!now.isBefore(verification.expiresAt)) {
            verificationRepository.delete(verification)
            return CheckVerificationResponse.Expired
        }
        val nextCheckAt = verification.lastCheckedAt?.plus(CHECK_INTERVAL)
        if (nextCheckAt != null && now.isBefore(nextCheckAt)) {
            val waitMillis = Duration.between(now, nextCheckAt).toMillis()
            return CheckVerificationResponse.TooEarly(retryAfterSeconds = (waitMillis + 999) / 1000)
        }
        verification.lastCheckedAt = now
        verificationRepository.save(verification)

        val codeFound =
            wareraClient
                .getCompanyIds(verification.wareraUserId)
                .asSequence()
                .map(wareraClient::getCompany)
                .any { it.name.contains(verification.code, ignoreCase = true) }
        if (!codeFound) return CheckVerificationResponse.CodeMissing

        link(verification)
        return CheckVerificationResponse.Linked(verification.wareraUsername)
    }

    /** Replaces any existing link of either account, so that each side is linked at most once. */
    private fun link(verification: AccountVerification) {
        transactionTemplate.executeWithoutResult {
            val now = clock.instant()
            val previous = linkRepository.findByWareraUserId(verification.wareraUserId)
            previous?.let(linkRepository::delete)
            linkRepository.deleteById(verification.discordUserId)
            // Remove the old rows before inserting, otherwise the unique constraints would fail.
            linkRepository.flush()
            linkRepository.save(
                AccountLink(
                    discordUserId = verification.discordUserId,
                    wareraUserId = verification.wareraUserId,
                    wareraUsername = verification.wareraUsername,
                    verifiedAt = now,
                ),
            )
            verificationRepository.deleteById(verification.discordUserId)
            val previousDiscordUserId = previous?.discordUserId?.takeIf { it != verification.discordUserId }
            applicationEvents.publishEvent(AccountLinked(verification.discordUserId, verification.wareraUserId, previousDiscordUserId))
            eventPublisher.publish(
                AccountLinkedEvent(
                    discordUserId = verification.discordUserId,
                    wareraUserId = verification.wareraUserId,
                    previousDiscordUserId = previousDiscordUserId,
                    occurredAt = now.toEpochMilli(),
                ),
            )
        }
    }

    /** WarEra has no lookup by name, so the search candidates are compared with the exact username. */
    private fun findUser(username: String): WareraUser? {
        if (username.isEmpty()) return null
        return wareraClient
            .searchUserIds(username)
            .asSequence()
            .map(wareraClient::getUser)
            .firstOrNull { it.username.equals(username, ignoreCase = true) }
    }

    private fun generateCode(): String =
        CODE_PREFIX + (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    private companion object {
        const val CODE_PREFIX = "BITS-"
        const val CODE_LENGTH = 6

        /** No characters that are easy to confuse, such as 0/O or 1/I/L. */
        const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        val CODE_VALIDITY: Duration = Duration.ofMinutes(15)
        val CHECK_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}
