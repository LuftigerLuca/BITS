package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.domain.AccountLink
import org.springframework.data.jpa.repository.JpaRepository

interface AccountLinkRepository : JpaRepository<AccountLink, String> {
    fun findByWareraUserId(wareraUserId: String): AccountLink?
}
