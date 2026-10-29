package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.DiscordRole
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.rolesync.domain.RuleBlock
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
                response(rule, role, status)
            }
    }

    /**
     * @throws ResponseStatusException 400 if the role cannot be given or a block or condition is invalid, see
     * [newBlocks]; 409 if the role has a rule already, which is to be edited instead
     */
    fun create(
        request: RuleRequest,
        actor: AuditActor,
    ): RuleResponse {
        val (role, blocks) = newBlocks(request)
        val saved =
            requireNotNull(
                transactionTemplate.execute {
                    if (ruleRepository.findByRoleId(role.id) != null) throw conflict(role.id)
                    val created =
                        try {
                            ruleRepository.saveAndFlush(RoleRule(role.id, blocks, createdAt = now()))
                        } catch (_: DataIntegrityViolationException) {
                            // The unique index on the role, if another admin was faster; the CHECK constraints and the
                            // index on the conditions of a block cannot fail after the checks of newBlocks.
                            throw conflict(role.id)
                        }
                    auditLog.record(actor, AuditAction.ROLE_RULE_CREATED, describe(role.id, role.name, created.label))
                    created
                },
            )
        syncTrigger.syncAllWithCachedData(confirmedRoleIds = listOf(role.id))
        return response(saved, role, RuleStatus.ACTIVE)
    }

    /**
     * Replaces the blocks of the rule of [roleId] as a whole.
     *
     * @throws ResponseStatusException 404 if the role has no rule, 400 like [create]
     */
    fun update(
        roleId: String,
        request: UpdateRuleRequest,
        actor: AuditActor,
    ): RuleResponse {
        val (role, blocks) = newBlocks(RuleRequest(roleId, request.blocks))
        val saved =
            requireNotNull(
                transactionTemplate.execute {
                    val rule = ruleRepository.findByRoleId(roleId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                    val before = rule.label
                    rule.replaceBlocks(blocks)
                    val updated = ruleRepository.saveAndFlush(rule)
                    auditLog.record(
                        actor,
                        AuditAction.ROLE_RULE_UPDATED,
                        "${describe(role.id, role.name, updated.label)} (vorher: $before)",
                    )
                    updated
                },
            )
        syncTrigger.syncAllWithCachedData(confirmedRoleIds = listOf(role.id))
        return response(saved, role, RuleStatus.ACTIVE)
    }

    /**
     * The blocks [request] asks for, not saved yet, with their role.
     *
     * @throws ResponseStatusException 400 if the role cannot be given; there are no or too many blocks, or a block has
     * no or too many conditions; a condition is incomplete or unknown; or the same condition stands twice in a block, or
     * the same block twice in the rule
     */
    fun newBlocks(request: RuleRequest): Pair<DiscordRole, List<RuleBlock>> {
        val role = roleRepository.findByIdOrNull(request.roleId)
        if (role == null || !role.assignable) throw badRequest("Role ${request.roleId} cannot be assigned")
        if (request.blocks.isEmpty()) throw badRequest("Blocks missing")
        if (request.blocks.size > MAX_BLOCKS) throw badRequest("At most $MAX_BLOCKS blocks")
        // The countries for every condition at once, and only if needed.
        val countries by lazy { wareraClient.getAllCountries().associate { it.id to it.name } }
        val blocks =
            request.blocks.map { conditions ->
                if (conditions.isEmpty()) throw badRequest("Conditions missing")
                if (conditions.size > MAX_CONDITIONS) throw badRequest("At most $MAX_CONDITIONS conditions per block")
                val block = RuleBlock(conditions.map { condition(it) { countryId -> countries[countryId] } })
                if (block.conditions.distinctBy { it.key }.size < block.conditions.size) throw badRequest("The same condition twice")
                block
            }
        if (blocks.distinctBy { it.key }.size < blocks.size) throw badRequest("The same block twice")
        return role to blocks
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
                    offices = request.offices.ifEmpty { throw badRequest("Offices missing") }.mapTo(sortedSetOf()) { it.office },
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

    /**
     * Deletes the rule of [roleId]; BITS no longer manages the role.
     *
     * @return the deleted rule, for the log
     * @throws ResponseStatusException 404 if the role has no rule
     */
    fun delete(
        roleId: String,
        actor: AuditActor,
    ): RoleRule {
        val rule =
            requireNotNull(
                transactionTemplate.execute {
                    val found = ruleRepository.findByRoleId(roleId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                    ruleRepository.delete(found)
                    val roleName = roleRepository.findByIdOrNull(found.roleId)?.name
                    auditLog.record(actor, AuditAction.ROLE_RULE_DELETED, describe(found.roleId, roleName, found.label))
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
        label: String,
    ): String {
        // The rule of a role deleted in Discord stays, so its name may be unknown by the time the rule is deleted.
        val role = roleName?.let { "@$it" } ?: "$roleId (in Discord gelöscht)"
        return "Rolle $role: $label"
    }

    private fun response(
        rule: RoleRule,
        role: DiscordRole?,
        status: RuleStatus,
    ) = RuleResponse(
        roleId = rule.roleId,
        roleName = role?.name,
        roleColor = role?.color,
        label = rule.label,
        blocks = rule.blocks.map { block -> BlockResponse(block.label, block.conditions.map(::conditionResponse)) },
        status = status,
    )

    private fun conditionResponse(condition: RuleCondition) =
        ConditionResponse(
            type = condition.type.ruleType,
            countryId = condition.countryId,
            offices = condition.offices.sorted().map { it.apiOffice },
            muId = condition.muId,
            threshold = condition.threshold,
            partyId = condition.partyId,
            muRole = condition.muRole?.apiRole,
            targetName = condition.targetName,
            label = condition.label,
        )

    private fun conflict(roleId: String) = ResponseStatusException(HttpStatus.CONFLICT, "Role $roleId has a rule already")

    private fun now() = clock.instant()

    private fun badRequest(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    companion object {
        /** Conditions per block; more would hardly be met by anybody. */
        const val MAX_CONDITIONS = 10

        /** Blocks per rule; like conditions, more would hardly be needed. */
        const val MAX_BLOCKS = 10

        private const val MAX_SEARCH_RESULTS = 5
    }
}
