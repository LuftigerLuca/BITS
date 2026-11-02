package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.domain.LanguagePreference
import org.springframework.data.jpa.repository.JpaRepository

interface LanguagePreferenceRepository : JpaRepository<LanguagePreference, String>
