package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/** Manages the rules; every change syncs all members, with the WarEra data of the last sync. */
@Service
class RoleRuleService(
    private val ruleRepository: RoleRuleRepository,
    private val roleRepository: DiscordRoleRepository,
    private val wareraClient: WareraClient,
    private val syncTrigger: RoleSyncTrigger,
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

    /** @throws ResponseStatusException 400 if the role cannot be given or the condition is incomplete or unknown */
    fun create(request: CreateRuleRequest): RuleResponse {
        val role = roleRepository.findByIdOrNull(request.roleId)
        if (role == null || !role.assignable) throw badRequest("Role ${request.roleId} cannot be assigned")
        val rule =
            when (request.type) {
                ConditionType.CITIZENSHIP ->
                    RoleRule(role.id, request.type, countryId = countryId(request), targetName = countryName(request), createdAt = now())
                ConditionType.GOVERNMENT_OFFICE ->
                    RoleRule(
                        role.id,
                        request.type,
                        countryId = countryId(request),
                        office = request.office ?: throw badRequest("Office missing"),
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
                        }
                    RoleRule(role.id, request.type, muId = mu.id, targetName = mu.name, createdAt = now())
                }
                ConditionType.LEVEL, ConditionType.MILITARY_RANK -> {
                    val threshold = request.threshold?.takeIf { it >= 1 } ?: throw badRequest("Threshold must be at least 1")
                    RoleRule(role.id, request.type, threshold = threshold, createdAt = now())
                }
            }
        val saved = ruleRepository.save(rule)
        syncTrigger.syncAllWithCachedData()
        return RuleResponse(requireNotNull(saved.id), role.id, role.name, role.color, saved.label, RuleStatus.ACTIVE)
    }

    fun delete(id: Long) {
        val rule = ruleRepository.findByIdOrNull(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        ruleRepository.delete(rule)
        syncTrigger.syncAllWithCachedData()
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

    private fun now() = clock.instant()

    private fun badRequest(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    private companion object {
        const val MAX_SEARCH_RESULTS = 5
    }
}
