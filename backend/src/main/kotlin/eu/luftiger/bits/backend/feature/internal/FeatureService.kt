package eu.luftiger.bits.backend.feature.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggled
import eu.luftiger.bits.backend.feature.api.FeatureToggles
import eu.luftiger.bits.backend.feature.domain.FeatureSetting
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * The switches under "Verwaltung". Every module asks for its own feature where it starts work, so a switch takes
 * effect with the next command or sync; the bot asks for them every 30 seconds, see [FeatureBotController].
 */
@Service
class FeatureService(
    private val repository: FeatureSettingRepository,
    private val auditLog: AuditLog,
    private val applicationEvents: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : FeatureToggles {
    override fun isEnabled(feature: BitsFeature): Boolean = repository.findByIdOrNull(feature)?.enabled ?: true

    /** All features in the order of [BitsFeature]. */
    fun all(): Map<BitsFeature, Boolean> {
        val stored = repository.findAll().associate { it.feature to it.enabled }
        return BitsFeature.entries.associateWith { stored[it] ?: true }
    }

    /** Switching a feature to the state it has already changes nothing and records nothing. */
    fun set(
        feature: BitsFeature,
        enabled: Boolean,
        actor: AuditActor,
    ) {
        transactionTemplate.executeWithoutResult {
            val setting = repository.findByIdOrNull(feature)
            if ((setting?.enabled ?: true) == enabled) return@executeWithoutResult
            if (setting == null) {
                repository.save(FeatureSetting(feature, enabled, clock.instant()))
            } else {
                setting.enabled = enabled
                setting.updatedAt = clock.instant()
            }
            auditLog.record(
                actor,
                AuditAction.FEATURE_TOGGLED,
                "${LABELS.getValue(feature)} ${if (enabled) "eingeschaltet" else "ausgeschaltet"}",
            )
            applicationEvents.publishEvent(FeatureToggled(feature, enabled))
        }
    }

    private companion object {
        /** As the dashboard names them, so the audit log reads the same. */
        val LABELS =
            mapOf(
                BitsFeature.VERIFICATION to "Verifizierung",
                BitsFeature.ROLE_SYNC to "Rollen-Sync",
                BitsFeature.NICKNAMES to "Nicknames",
                BitsFeature.PROFILE to "Profil",
                BitsFeature.VOTES to "Abstimmungen",
                BitsFeature.EMBASSIES to "Botschaften",
            )
    }
}
