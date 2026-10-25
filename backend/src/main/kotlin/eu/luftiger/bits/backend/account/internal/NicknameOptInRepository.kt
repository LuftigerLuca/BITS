package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.domain.NicknameOptIn
import org.springframework.data.jpa.repository.JpaRepository

interface NicknameOptInRepository : JpaRepository<NicknameOptIn, String>
