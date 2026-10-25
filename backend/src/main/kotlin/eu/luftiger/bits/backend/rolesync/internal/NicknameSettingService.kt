package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.domain.NicknameSetting
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** Whether the role sync sets nicknames to WarEra names; off until an admin turns it on. */
@Service
class NicknameSettingService(
    private val repository: NicknameSettingRepository,
    private val syncTrigger: RoleSyncTrigger,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /** The role sync reads the repository itself, since this service depends on the [RoleSyncTrigger]. */
    fun enabled(): Boolean = repository.findByIdOrNull(NicknameSetting.SINGLE_ROW_ID)?.enabled == true

    /** Turning it on syncs everybody right away, so the nicknames do not wait for the next hour. */
    fun setEnabled(
        enabled: Boolean,
        actor: AuditActor,
    ) {
        val changed =
            transactionTemplate.execute {
                val setting = repository.findByIdOrNull(NicknameSetting.SINGLE_ROW_ID)
                if ((setting?.enabled == true) == enabled) return@execute false
                if (setting == null) {
                    repository.save(NicknameSetting(enabled, clock.instant()))
                } else {
                    setting.enabled = enabled
                    setting.updatedAt = clock.instant()
                }
                val description = if (enabled) "Nicknames aus WarEra-Namen eingeschaltet" else "Nicknames aus WarEra-Namen ausgeschaltet"
                auditLog.record(actor, AuditAction.NICKNAME_SETTING_CHANGED, description)
                true
            } == true
        if (changed && enabled) syncTrigger.syncAllForSettingChange()
    }
}
