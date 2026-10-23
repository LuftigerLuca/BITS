package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.domain.AccountVerification
import org.springframework.data.jpa.repository.JpaRepository

interface AccountVerificationRepository : JpaRepository<AccountVerification, String>
