package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.api.AccountSelfService
import eu.luftiger.bits.backend.account.api.dto.StoredAccountData
import eu.luftiger.bits.backend.account.api.dto.StoredLink
import eu.luftiger.bits.backend.account.api.dto.StoredVerification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountDataService(
    private val linkRepository: AccountLinkRepository,
    private val verificationRepository: AccountVerificationRepository,
    private val optInRepository: NicknameOptInRepository,
    private val verificationService: VerificationService,
) : AccountSelfService {
    @Transactional(readOnly = true)
    override fun storedData(discordUserId: String) =
        StoredAccountData(
            link =
                linkRepository.findByIdOrNull(discordUserId)?.let {
                    StoredLink(it.wareraUserId, it.wareraUsername, it.verifiedAt)
                },
            verification =
                verificationRepository.findByIdOrNull(discordUserId)?.let {
                    StoredVerification(it.wareraUserId, it.wareraUsername, it.code, it.expiresAt)
                },
            nicknameOptedInAt = optInRepository.findByIdOrNull(discordUserId)?.optedInAt,
        )

    override fun unlink(discordUserId: String): Boolean = verificationService.unlink(discordUserId).wareraUsername != null
}
