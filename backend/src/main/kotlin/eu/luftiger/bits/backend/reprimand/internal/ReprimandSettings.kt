package eu.luftiger.bits.backend.reprimand.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.reprimand.domain.ReprimandRole
import eu.luftiger.bits.backend.reprimand.domain.ReprimandSetting
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.rolesync.api.dto.GuildRole
import eu.luftiger.bits.backend.shared.InvalidInputException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** Which roles, besides admins, may reprimand, and how long the timeout of a reprimand lasts. */
@Service
class ReprimandSettings(
    private val roleRepository: ReprimandRoleRepository,
    private val settingRepository: ReprimandSettingRepository,
    private val guildRoles: GuildRoles,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /** Whether an admin, or a member of [roleIds], may reprimand; also whether such a member is protected from it. */
    fun mayReprimand(
        admin: Boolean,
        roleIds: Collection<String>,
    ): Boolean {
        if (admin) return true
        val reprimandRoles = reprimandRoleIds().toHashSet()
        return roleIds.any { it in reprimandRoles }
    }

    fun reprimandRoleIds(): List<String> = roleRepository.findAll().map { it.roleId }.sorted()

    fun timeoutMinutes(): Int = settingRepository.findByIdOrNull(ReprimandSetting.SINGLE_ROW_ID)?.timeoutMinutes ?: DEFAULT_TIMEOUT_MINUTES

    /** The roles of the guild to pick from, highest first. */
    fun roles(): List<GuildRole> = guildRoles.all()

    /**
     * Only admins get here, see the controller. Saving what is set already records nothing.
     *
     * @throws InvalidInputException if a role is unknown or the timeout is out of range
     */
    fun set(
        roleIds: List<String>,
        timeoutMinutes: Int,
        actor: AuditActor,
    ) {
        if (timeoutMinutes !in MIN_TIMEOUT_MINUTES..MAX_TIMEOUT_MINUTES) throw InvalidInputException("Timeout out of range")
        val known = guildRoles.all().associateBy { it.id }
        val unknown = roleIds.filter { it !in known }
        if (unknown.isNotEmpty()) throw InvalidInputException("Unknown roles $unknown")
        val distinct = roleIds.distinct()
        transactionTemplate.executeWithoutResult {
            if (distinct.sorted() == reprimandRoleIds() && timeoutMinutes == timeoutMinutes()) return@executeWithoutResult
            roleRepository.deleteAllInBatch()
            roleRepository.saveAll(distinct.map(::ReprimandRole))
            val setting = settingRepository.findByIdOrNull(ReprimandSetting.SINGLE_ROW_ID)
            if (setting == null) {
                settingRepository.save(ReprimandSetting(timeoutMinutes, clock.instant()))
            } else {
                setting.timeoutMinutes = timeoutMinutes
                setting.updatedAt = clock.instant()
            }
            val names = distinct.mapNotNull { known[it]?.name }.sorted()
            val who = if (names.isEmpty()) "nur Admins" else names.joinToString { "@$it" }
            auditLog.record(
                actor,
                AuditAction.REPRIMAND_SETTINGS_CHANGED,
                "Rügen: dürfen $who; Timeout ${Durations.german(timeoutMinutes)}",
            )
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MINUTES = 10
        const val MIN_TIMEOUT_MINUTES = 1

        /** Discord's limit for a timeout: 28 days. */
        const val MAX_TIMEOUT_MINUTES = 28 * 24 * 60
    }
}

/** Durations in the largest unit that fits exactly, as the dashboard shows them. */
object Durations {
    fun german(minutes: Int): String =
        when {
            minutes % DAY == 0 -> plural(minutes / DAY, "Tag", "Tage")
            minutes % HOUR == 0 -> plural(minutes / HOUR, "Stunde", "Stunden")
            else -> plural(minutes, "Minute", "Minuten")
        }

    private fun plural(
        count: Int,
        one: String,
        many: String,
    ) = "$count ${if (count == 1) one else many}"

    private const val HOUR = 60
    private const val DAY = 24 * HOUR
}
