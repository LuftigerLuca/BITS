package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.DiscordRole
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.warera.WareraBadRequestException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/**
 * Manages the rules; every change syncs all members, with the WarEra data of the last sync. The change is the admin's
 * decision, so the bot takes the role from as many members as it takes, see [eu.luftiger.bits.shared.RoleSyncEvent].
 *
 * A change and its entry in the [AuditLog] are committed together. The sync starts only after the commit, since it
 * reads the rules on a thread of its own.
 */
@Service
class RoleRuleService(
    private val ruleRepository: RoleRuleRepository,
    private val roleRepository: DiscordRoleRepository,
    private val wareraClient: WareraClient,
    private val syncTrigger: RoleSyncTrigger,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    /** Roles the bot can give, highest first; the others cannot be picked for a rule. */
    fun assignableRoles(): List<RoleResponse> {
        val managed = ruleRepository.findAll().mapTo(HashSet()) { it.roleId }
        return roleRepository
            .findAll()
            .filter { it.assignable }
            .sortedByDescending { it.position }
            .map { RoleResponse(it.id, it.name, it.color, managed = it.id in managed) }
    }

    fun rules(): List<RuleResponse> {
        val roles = roleRepository.findAll().associateBy { it.id }
        return ruleRepository
            .findAll()
            .sortedWith(compareByDescending<RoleRule> { roles[it.roleId]?.position ?: Int.MIN_VALUE }.thenBy { it.id })
            .map { rule ->
                val role = roles[rule.roleId]
                val status =
                    when {
                        role == null -> RuleStatus.ORPHANED
                        !role.assignable -> RuleStatus.NOT_ASSIGNABLE
                        else -> RuleStatus.ACTIVE
                    }
                RuleResponse(requireNotNull(rule.id), rule.roleId, role?.name, role?.color, rule.label, status)
            }
    }

    /**
     * @throws ResponseStatusException 400 if the role cannot be given or the condition is incomplete or unknown, 409 if
     * the role has a rule with the same condition already
     */
    fun create(
        request: CreateRuleRequest,
        actor: AuditActor,
    ): RuleResponse {
        val (role, rule) = newRule(request)
        val saved =
            requireNotNull(
                transactionTemplate.execute {
                    val created =
                        try {
                            ruleRepository.saveAndFlush(rule)
                        } catch (_: DataIntegrityViolationException) {
                            // The unique index on role and condition; the CHECK constraints cannot fail after the
                            // checks of newRule.
                            throw ResponseStatusException(HttpStatus.CONFLICT, "Role ${role.id} has this rule already")
                        }
                    auditLog.record(actor, AuditAction.ROLE_RULE_CREATED, describe(role.id, role.name, created))
                    created
                },
            )
        syncTrigger.syncAllWithCachedData(confirmedRoleIds = listOf(role.id))
        return RuleResponse(requireNotNull(saved.id), role.id, role.name, role.color, saved.label, RuleStatus.ACTIVE)
    }

    /**
     * The rule [request] asks for, not saved yet, with its role.
     *
     * @throws ResponseStatusException 400 if the role cannot be given or the condition is incomplete or unknown
     */
    fun newRule(request: CreateRuleRequest): Pair<DiscordRole, RoleRule> {
        val role = roleRepository.findByIdOrNull(request.roleId)
        if (role == null || !role.assignable) throw badRequest("Role ${request.roleId} cannot be assigned")
        val rule =
            when (request.type.condition) {
                ConditionType.CITIZENSHIP ->
                    RoleRule(
                        role.id,
                        request.type.condition,
                        countryId = countryId(request),
                        targetName = countryName(request),
                        createdAt = now(),
                    )
                ConditionType.GOVERNMENT_OFFICE ->
                    RoleRule(
                        role.id,
                        request.type.condition,
                        countryId = countryId(request),
                        office = request.office?.office ?: throw badRequest("Office missing"),
                        targetName = countryName(request),
                        createdAt = now(),
                    )
                ConditionType.MILITARY_UNIT -> {
                    val muId = request.muId?.takeIf { it.isNotBlank() } ?: throw badRequest("Military unit missing")
                    val mu =
                        try {
                            wareraClient.getMilitaryUnit(muId)
                        } catch (_: WareraNotFoundException) {
                            // Without the cause, which would let the 503 handler for WarEra errors take over.
                            throw badRequest("Unknown military unit $muId")
                        } catch (_: WareraBadRequestException) {
                            // An ID in a format WarEra does not accept.
                            throw badRequest("Unknown military unit $muId")
                        }
                    RoleRule(role.id, request.type.condition, muId = mu.id, targetName = mu.name, createdAt = now())
                }
                ConditionType.LEVEL, ConditionType.MILITARY_RANK -> {
                    val threshold = request.threshold?.takeIf { it >= 1 } ?: throw badRequest("Threshold must be at least 1")
                    RoleRule(role.id, request.type.condition, threshold = threshold, createdAt = now())
                }
            }
        return role to rule
    }

    /** @return the deleted rule, for the log */
    fun delete(
        id: Long,
        actor: AuditActor,
    ): RoleRule {
        val rule =
            requireNotNull(
                transactionTemplate.execute {
                    val found = ruleRepository.findByIdOrNull(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                    ruleRepository.delete(found)
                    val roleName = roleRepository.findByIdOrNull(found.roleId)?.name
                    auditLog.record(actor, AuditAction.ROLE_RULE_DELETED, describe(found.roleId, roleName, found))
                    found
                },
            )
        syncTrigger.syncAllWithCachedData(confirmedRoleIds = listOf(rule.roleId))
        return rule
    }

    fun countries(): List<OptionResponse> = wareraClient.getAllCountries().map { OptionResponse(it.id, it.name) }.sortedBy { it.name }

    /** WarEra's search returns the best few matches; each needs its own call for the name. */
    fun searchMilitaryUnits(query: String): List<OptionResponse> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        return wareraClient
            .searchMilitaryUnitIds(text)
            .take(MAX_SEARCH_RESULTS)
            .map(wareraClient::getMilitaryUnit)
            .map { OptionResponse(it.id, it.name) }
    }

    private fun countryId(request: CreateRuleRequest): String =
        request.countryId?.takeIf { it.isNotBlank() } ?: throw badRequest("Country missing")

    private fun countryName(request: CreateRuleRequest): String {
        val countryId = countryId(request)
        return wareraClient.getAllCountries().firstOrNull { it.id == countryId }?.name ?: throw badRequest("Unknown country $countryId")
    }

    /** A snapshot for the audit log, readable after the rule and the role are gone; e.g. "Rolle @Bürger: Level ab 10". */
    private fun describe(
        roleId: String,
        roleName: String?,
        rule: RoleRule,
    ): String {
        // The rules of a role deleted in Discord stay, so its name may be unknown by the time a rule is deleted.
        val role = roleName?.let { "@$it" } ?: "$roleId (in Discord gelöscht)"
        return "Rolle $role: ${rule.label}"
    }

    private fun now() = clock.instant()

    private fun badRequest(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    private companion object {
        const val MAX_SEARCH_RESULTS = 5
    }
}
