package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.DiscordRole
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleCondition
import eu.luftiger.bits.warera.WareraBadRequestException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraMilitaryUnit
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
     * @throws ResponseStatusException 400 if the role cannot be given or a condition is incomplete or unknown, 409 if
     * the role has a rule with the same conditions already
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
                            // The unique index on role and conditions; the CHECK constraints and the index on the
                            // conditions of a rule cannot fail after the checks of newRule.
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
     * @throws ResponseStatusException 400 if the role cannot be given, there are no or too many conditions, one is
     * incomplete or unknown, or the same condition is given twice
     */
    fun newRule(request: CreateRuleRequest): Pair<DiscordRole, RoleRule> {
        val role = roleRepository.findByIdOrNull(request.roleId)
        if (role == null || !role.assignable) throw badRequest("Role ${request.roleId} cannot be assigned")
        if (request.conditions.isEmpty()) throw badRequest("Conditions missing")
        if (request.conditions.size > MAX_CONDITIONS) throw badRequest("At most $MAX_CONDITIONS conditions")
        // The countries for every condition at once, and only if needed.
        val countries by lazy { wareraClient.getAllCountries().associate { it.id to it.name } }
        val conditions = request.conditions.map { condition(it) { countryId -> countries[countryId] } }
        if (conditions.distinctBy { it.key }.size < conditions.size) throw badRequest("The same condition twice")
        return role to RoleRule(role.id, conditions, createdAt = now())
    }

    private fun condition(
        request: ConditionRequest,
        countryName: (countryId: String) -> String?,
    ): RuleCondition {
        val type = request.type.condition
        return when (type) {
            ConditionType.CITIZENSHIP -> {
                val countryId = countryId(request)
                RuleCondition(type, countryId = countryId, targetName = knownCountry(countryId, countryName))
            }
            ConditionType.GOVERNMENT_OFFICE -> {
                val countryId = countryId(request)
                RuleCondition(
                    type,
                    countryId = countryId,
                    office = request.office?.office ?: throw badRequest("Office missing"),
                    targetName = knownCountry(countryId, countryName),
                )
            }
            ConditionType.MILITARY_UNIT -> {
                val muId = request.muId?.takeIf { it.isNotBlank() } ?: throw badRequest("Military unit missing")
                val mu = knownMilitaryUnit(muId)
                RuleCondition(type, muId = mu.id, targetName = mu.name)
            }
            ConditionType.MU_LEADERSHIP -> {
                val role = request.muRole?.role ?: throw badRequest("Rank missing")
                // Without a unit, any unit counts.
                val mu = request.muId?.takeIf { it.isNotBlank() }?.let(::knownMilitaryUnit)
                RuleCondition(type, muId = mu?.id, targetName = mu?.name, muRole = role)
            }
            ConditionType.PARTY -> {
                val countryId = countryId(request)
                val partyId = request.partyId?.takeIf { it.isNotBlank() } ?: throw badRequest("Party missing")
                knownCountry(countryId, countryName)
                val party =
                    wareraClient.getParties(countryId).firstOrNull { it.id == partyId } ?: throw badRequest("Unknown party $partyId")
                RuleCondition(type, countryId = countryId, partyId = party.id, targetName = party.name)
            }
            ConditionType.PARTY_LEADER -> {
                val countryId = countryId(request)
                val country = knownCountry(countryId, countryName)
                // Without a party, any party of the country counts.
                val party =
                    request.partyId?.takeIf { it.isNotBlank() }?.let { partyId ->
                        wareraClient.getParties(countryId).firstOrNull { it.id == partyId } ?: throw badRequest("Unknown party $partyId")
                    }
                RuleCondition(type, countryId = countryId, partyId = party?.id, targetName = party?.name ?: country)
            }
            ConditionType.LEVEL, ConditionType.MILITARY_RANK, ConditionType.COMPANIES -> {
                val threshold = request.threshold?.takeIf { it >= 1 } ?: throw badRequest("Threshold must be at least 1")
                RuleCondition(type, threshold = threshold)
            }
        }
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

    /** The parties of a known country, by name. */
    fun parties(countryId: String): List<OptionResponse> {
        if (wareraClient.getAllCountries().none { it.id == countryId }) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return wareraClient.getParties(countryId).map { OptionResponse(it.id, it.name) }.sortedBy { it.name }
    }

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

    private fun knownMilitaryUnit(muId: String): WareraMilitaryUnit =
        try {
            wareraClient.getMilitaryUnit(muId)
        } catch (_: WareraNotFoundException) {
            // Without the cause, which would let the 503 handler for WarEra errors take over.
            throw badRequest("Unknown military unit $muId")
        } catch (_: WareraBadRequestException) {
            // An ID in a format WarEra does not accept.
            throw badRequest("Unknown military unit $muId")
        }

    private fun countryId(request: ConditionRequest): String =
        request.countryId?.takeIf { it.isNotBlank() } ?: throw badRequest("Country missing")

    private fun knownCountry(
        countryId: String,
        countryName: (countryId: String) -> String?,
    ): String = countryName(countryId) ?: throw badRequest("Unknown country $countryId")

    /**
     * A snapshot for the audit log, readable after the rule and the role are gone; e.g. "Rolle @Bürger:
     * Staatsbürgerschaft Germany und Level ab 10".
     */
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

    companion object {
        /** More would hardly be met by anybody, and keeps the key of the conditions short enough for its index. */
        const val MAX_CONDITIONS = 10

        private const val MAX_SEARCH_RESULTS = 5
    }
}
