package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.api.AccountLinked
import eu.luftiger.bits.backend.account.api.AccountUnlinked
import eu.luftiger.bits.backend.account.domain.AccountLink
import eu.luftiger.bits.backend.account.domain.AccountVerification
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.shared.AccountLinkedEvent
import eu.luftiger.bits.shared.CheckVerificationResponse
import eu.luftiger.bits.shared.StartVerificationResponse
import eu.luftiger.bits.shared.UnlinkAccountResponse
import eu.luftiger.bits.shared.VerificationCheckedEvent
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraUser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.task.TaskExecutor
import org.springframework.dao.DataIntegrityViolationException
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
 * This holds only if nobody but the owner can rename a company. In WarEra's data a company has exactly one owner
 * (`user`) and no managers or other roles; workers are only counted (checked against the API in October 2026). To be
 * safe, the code counts only in companies whose owner is the user. What no check can catch is an owner who is talked
 * into putting somebody else's code into a company name, so `/verify` warns about exactly that.
 *
 * WarEra calls run outside of transactions, since they can take a while when the client is throttled. For the same
 * reason the check runs in the background and reports its result as [VerificationCheckedEvent]: the bot would
 * otherwise give up waiting while the backend still links the accounts.
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
    @param:Qualifier("applicationTaskExecutor") private val executor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)
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
        // Atomic, so that two quick clicks do not both call WarEra and link twice.
        val claimed =
            transactionTemplate.execute {
                verificationRepository.claimCheck(discordUserId, verification.code, now, now - CHECK_INTERVAL)
            } == 1
        if (!claimed) {
            val current = verificationRepository.findByIdOrNull(discordUserId)
            val lastCheckedAt = current?.lastCheckedAt
            if (current?.code != verification.code || lastCheckedAt == null) return CheckVerificationResponse.Invalid
            val waitMillis = Duration.between(now, lastCheckedAt + CHECK_INTERVAL).toMillis().coerceAtLeast(0)
            return CheckVerificationResponse.TooEarly(retryAfterSeconds = (waitMillis + 999) / 1000)
        }

        executor.execute {
            val result = lookForCode(verification) ?: return@execute
            eventPublisher.publish(VerificationCheckedEvent(discordUserId, verification.code, result, clock.millis()))
        }
        return CheckVerificationResponse.Checking
    }

    /** Returns `null` on an unexpected error; the bot then tells the user to try again once it stops waiting. */
    private fun lookForCode(verification: AccountVerification): CheckVerificationResponse? =
        try {
            val codeFound =
                wareraClient
                    .getCompanyIds(verification.wareraUserId)
                    .asSequence()
                    .map(wareraClient::getCompany)
                    .any { it.ownerId == verification.wareraUserId && it.name.contains(verification.code, ignoreCase = true) }
            when {
                !codeFound -> CheckVerificationResponse.CodeMissing
                link(verification) -> CheckVerificationResponse.Linked(verification.wareraUsername)
                else -> CheckVerificationResponse.Invalid
            }
        } catch (e: WareraApiException) {
            log.warn("WarEra API not reachable during a verification check", e)
            CheckVerificationResponse.WareraUnavailable
        } catch (e: Exception) {
            log.error("Verification check failed", e)
            null
        }

    /**
     * Replaces any existing link of either account, so that each side is linked at most once. Returns `false` if the
     * code is no longer the user's current one, e.g. because a newer one was issued during the check.
     *
     * Two checks that link the same account at the same time do not see each other's link, so one of them fails on a
     * unique constraint. It tries once more, then sees the other link and replaces it like any older one.
     */
    private fun link(verification: AccountVerification): Boolean =
        try {
            tryLink(verification)
        } catch (e: DataIntegrityViolationException) {
            log.info("Account of user {} was linked by another check at the same time, trying again", verification.discordUserId)
            tryLink(verification)
        }

    private fun tryLink(verification: AccountVerification): Boolean =
        transactionTemplate.execute {
            val current = verificationRepository.findByIdOrNull(verification.discordUserId)
            if (current?.code != verification.code) return@execute false
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
            true
        } == true

    /**
     * Deletes the link and any open code of the user, everything BITS keeps about a Discord account. The role sync
     * then takes the managed roles away, since an account without link meets no rule.
     */
    fun unlink(discordUserId: String): UnlinkAccountResponse =
        transactionTemplate.execute {
            val link = linkRepository.findByIdOrNull(discordUserId)
            link?.let(linkRepository::delete)
            verificationRepository.findByIdOrNull(discordUserId)?.let(verificationRepository::delete)
            if (link != null) {
                log.info("User {} unlinked WarEra account {}", discordUserId, link.wareraUserId)
                applicationEvents.publishEvent(AccountUnlinked(discordUserId))
            }
            UnlinkAccountResponse(link?.wareraUsername)
        }!!

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
