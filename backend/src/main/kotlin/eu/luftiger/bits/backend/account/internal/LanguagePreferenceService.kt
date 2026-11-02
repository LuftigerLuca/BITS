package eu.luftiger.bits.backend.account.internal

import eu.luftiger.bits.backend.account.domain.LanguagePreference
import eu.luftiger.bits.backend.account.domain.PreferredLanguage
import eu.luftiger.bits.shared.Language
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Members choose their language themselves, also without a link; automatic keeps nothing about them. */
@Service
class LanguagePreferenceService(
    private val repository: LanguagePreferenceRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(discordUserId: String): Language? =
        repository.findByIdOrNull(discordUserId)?.language?.let {
            when (it) {
                PreferredLanguage.GERMAN -> Language.GERMAN
                PreferredLanguage.ENGLISH -> Language.ENGLISH
            }
        }

    @Transactional
    fun set(
        discordUserId: String,
        language: Language?,
    ) {
        val existing = repository.findByIdOrNull(discordUserId)
        if (language == null) {
            existing?.let(repository::delete)
            return
        }
        val preferred =
            when (language) {
                Language.GERMAN -> PreferredLanguage.GERMAN
                Language.ENGLISH -> PreferredLanguage.ENGLISH
            }
        if (existing == null) {
            repository.save(LanguagePreference(discordUserId, preferred, clock.instant()))
        } else {
            existing.language = preferred
            existing.updatedAt = clock.instant()
        }
    }
}
