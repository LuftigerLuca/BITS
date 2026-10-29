package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.domain.RoleSyncSetting
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * The card "Schutz vor Massenentzug" of the page "Rollen": whether the bot holds back a role that a sync of all members
 * would take from many of its holders at once, see [eu.luftiger.bits.shared.RoleSyncEvent.removalLimit]. It is on until
 * an admin switches it off, e.g. when a real change takes a role from many members and changing its rule is no option.
 *
 * A change is recorded in the audit log in its transaction. Switching it off syncs all members once committed, so the
 * removals held back so far are applied right away; switching it on changes nothing until the next sync.
 */
@Service
class RemovalLimitService(
    private val repository: RoleSyncSettingRepository,
    private val syncTrigger: RoleSyncTrigger,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    fun isEnabled(): Boolean = repository.findByIdOrNull(RoleSyncSetting.SINGLE_ROW_ID)?.removalLimitEnabled ?: true

    /** Switching it to the state it has already changes nothing and records nothing. */
    fun set(
        enabled: Boolean,
        actor: AuditActor,
    ) {
        if (isEnabled() == enabled) return
        transactionTemplate.executeWithoutResult {
            val setting = repository.findByIdOrNull(RoleSyncSetting.SINGLE_ROW_ID)
            if (setting == null) {
                repository.save(RoleSyncSetting(enabled, clock.instant()))
            } else {
                setting.removalLimitEnabled = enabled
                setting.updatedAt = clock.instant()
            }
            auditLog.record(
                actor,
                AuditAction.REMOVAL_LIMIT_TOGGLED,
                "Schutz vor Massenentzug ${if (enabled) "eingeschaltet" else "ausgeschaltet"}",
            )
        }
        if (!enabled) syncTrigger.syncAllAfterSettingChange()
    }
}
