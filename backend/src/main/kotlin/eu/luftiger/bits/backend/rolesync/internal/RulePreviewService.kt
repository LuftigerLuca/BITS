package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.members.api.GuildMembers
import eu.luftiger.bits.backend.members.api.dto.GuildMember
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Shows an admin whom a rule change gives the role and from whom it takes it, before it is saved. It judges like the
 * sync that follows the change: with the [RuleEvaluator], the WarEra data of the last sync and fresh governments
 * and parties, against the roles the members hold now as the bot reported them. A rule change only changes its own role, and the bot
 * takes it from as many members as it takes, so that is all the sync would change for it.
 *
 * It saves, records and publishes nothing.
 */
@Service
class RulePreviewService(
    private val ruleService: RoleRuleService,
    private val ruleRepository: RoleRuleRepository,
    private val roleRepository: DiscordRoleRepository,
    private val ruleInputs: RuleInputs,
    private val syncService: RoleSyncService,
    private val accountLinks: AccountLinks,
    private val guildMembers: GuildMembers,
) {
    /** @throws ResponseStatusException 400 like [RoleRuleService.create] */
    fun previewCreate(request: CreateRuleRequest): RulePreviewResponse {
        val (role, rule) = ruleService.newRule(request)
        return preview(role.id, rulesOf(role.id) + rule)
    }

    /** @throws ResponseStatusException 404 if the rule does not exist */
    fun previewDelete(id: Long): RulePreviewResponse {
        val rule = ruleRepository.findByIdOrNull(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return preview(rule.roleId, rulesOf(rule.roleId).filter { it.id != id })
    }

    private fun rulesOf(roleId: String) = ruleInputs.activeRules().filter { it.roleId == roleId }

    /** [rules] are the active rules of [roleId] after the change. */
    private fun preview(
        roleId: String,
        rules: List<RoleRule>,
    ): RulePreviewResponse {
        val roleName = roleRepository.findByIdOrNull(roleId)?.name
        val members = guildMembers.members()
        // Without rules, BITS no longer manages the role, so the sync leaves it alone.
        if (rules.isEmpty() || members == null) {
            return RulePreviewResponse(
                roleId,
                roleName,
                managed = rules.isNotEmpty(),
                membersReported = members != null,
                usersLoaded = syncService.loadedAll(),
                gains = emptyList(),
                losses = emptyList(),
                unknown = emptyList(),
            )
        }
        val evaluator = RuleEvaluator(rules, ruleInputs.ruleData(rules))
        val links = accountLinks.findAll().associate { it.discordUserId to it.wareraUserId }
        val users = syncService.lastLoadedUsers()
        val gains = mutableListOf<GuildMember>()
        val losses = mutableListOf<GuildMember>()
        val unknown = mutableListOf<GuildMember>()
        members.forEach { member ->
            val wareraUserId = links[member.discordUserId]
            val outcome =
                when {
                    // Unlinked members meet no rule.
                    wareraUserId == null -> RoleOutcome.NOT_MET
                    // The sync would load the user first.
                    wareraUserId !in users -> RoleOutcome.UNDECIDED
                    // Companies the sync would load first are undecided, unless another condition fails.
                    else -> users.getValue(wareraUserId).let { evaluator.check(it.user, it.companyCount).single().outcome }
                }
            val holds = roleId in member.roleIds
            when (outcome) {
                RoleOutcome.MET -> if (!holds) gains += member
                RoleOutcome.NOT_MET -> if (holds) losses += member
                RoleOutcome.UNDECIDED -> unknown += member
            }
        }
        return RulePreviewResponse(
            roleId,
            roleName,
            managed = true,
            membersReported = true,
            usersLoaded = syncService.loadedAll(),
            gains = gains.toPreview(),
            losses = losses.toPreview(),
            unknown = unknown.toPreview(),
        )
    }

    private fun List<GuildMember>.toPreview() =
        map { PreviewMember(it.discordUserId, it.name) }.sortedWith(compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.name })
}
