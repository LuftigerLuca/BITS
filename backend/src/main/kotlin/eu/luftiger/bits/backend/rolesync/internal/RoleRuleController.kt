package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.MilitaryUnitRole
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Rules of the role sync in the dashboard. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/role-sync")
class RoleRuleController(
    private val ruleService: RoleRuleService,
    private val previewService: RulePreviewService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/roles")
    fun roles(): List<RoleResponse> = ruleService.assignableRoles()

    @GetMapping("/rules")
    fun rules(): List<RuleResponse> = ruleService.rules()

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: RuleRequest,
        @AuthenticationPrincipal session: Jwt,
    ): RuleResponse {
        val rule = ruleService.create(request, actor(session))
        log.info("Rule ({}) for role {} created by {}", rule.label, rule.roleId, session.subject)
        return rule
    }

    /** Replaces the blocks of the rule of the role as a whole. */
    @PutMapping("/rules/{roleId}")
    fun update(
        @PathVariable roleId: String,
        @RequestBody request: UpdateRuleRequest,
        @AuthenticationPrincipal session: Jwt,
    ): RuleResponse {
        val rule = ruleService.update(roleId, request, actor(session))
        log.info("Rule ({}) for role {} updated by {}", rule.label, rule.roleId, session.subject)
        return rule
    }

    /** What the rule would change as the rule of its role, in place of the one it has, without saving it. */
    @PostMapping("/rules/preview")
    fun preview(
        @RequestBody request: RuleRequest,
    ): RulePreviewResponse = previewService.preview(request)

    /** What deleting the rule of the role would change, without deleting it. */
    @GetMapping("/rules/{roleId}/deletion-preview")
    fun previewDelete(
        @PathVariable roleId: String,
    ): RulePreviewResponse = previewService.previewDelete(roleId)

    @DeleteMapping("/rules/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable roleId: String,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val rule = ruleService.delete(roleId, actor(session))
        log.info("Rule ({}) for role {} deleted by {}", rule.label, roleId, session.subject)
    }

    @GetMapping("/countries")
    fun countries(): List<OptionResponse> = ruleService.countries()

    @GetMapping("/countries/{countryId}/parties")
    fun parties(
        @PathVariable countryId: String,
    ): List<OptionResponse> = ruleService.parties(countryId)

    @GetMapping("/military-units")
    fun militaryUnits(
        @RequestParam query: String,
    ): List<OptionResponse> = ruleService.searchMilitaryUnits(query)

    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
}

@Serializable
data class RoleResponse(
    val id: String,
    val name: String,
    val color: Int?,
    /** Whether a rule exists for the role already, i.e. BITS manages it. */
    val managed: Boolean,
    /** Name of the DSK the role belongs to, `null` if none; such a role cannot get a rule. */
    val dskName: String?,
    /** Whether it is the role for verified or unverified members; such a role cannot get a rule. */
    val statusRole: Boolean,
)

/** The rule of a role, the only one it has. */
@Serializable
data class RuleResponse(
    val roleId: String,
    /** `null` if the role was deleted in Discord. */
    val roleName: String?,
    val roleColor: Int?,
    /** The blocks joined with "oder". */
    val label: String,
    /** Of these, one is enough. */
    val blocks: List<BlockResponse>,
    val status: RuleStatus,
)

/** All of the [conditions] must be met. */
@Serializable
data class BlockResponse(
    /** The conditions joined with "und". */
    val label: String,
    val conditions: List<ConditionResponse>,
)

/** A condition with the fields of its [ConditionRequest], so the dashboard can edit it. */
@Serializable
data class ConditionResponse(
    val type: RuleType,
    val countryId: String?,
    val offices: List<Office>,
    val muId: String?,
    val threshold: Int?,
    val partyId: String?,
    val muRole: MuRole?,
    /** Name of the country, military unit or party at the time the condition was saved. */
    val targetName: String?,
    val label: String,
)

@Serializable
enum class RuleStatus {
    ACTIVE,

    /** The role was deleted in Discord; the rule is not run. */
    ORPHANED,

    /** The role exists, but the bot cannot give it, e.g. because it is above the bot's highest role. */
    NOT_ASSIGNABLE,
}

/**
 * Whom a rule change gives its role and from whom it takes it, for the members the bot reported last. Members whose
 * WarEra data is missing are [unknown]: the sync keeps their role as it is, or loads their data first.
 */
@Serializable
data class RulePreviewResponse(
    val roleId: String,
    /** `null` if the role was deleted in Discord. */
    val roleName: String?,
    /** `false` if the role has no active rule after the change: BITS no longer manages it, and nobody gets or loses it. */
    val managed: Boolean,
    /** `false` until the bot has reported the members with their roles since the backend started; there is no preview. */
    val membersReported: Boolean,
    /** `false` until the WarEra data of all linked members was loaded since the backend started; many are [unknown]. */
    val usersLoaded: Boolean,
    // Without defaults: kotlinx leaves out values equal to their default, and the dashboard expects every list.
    val gains: List<PreviewMember>,
    val losses: List<PreviewMember>,
    val unknown: List<PreviewMember>,
)

@Serializable
data class PreviewMember(
    val discordUserId: String,
    /** `null` for a member who joined after the bot's last list. */
    val name: String?,
)

/**
 * One of the [blocks] must be met, and all conditions of a block; at least one and at most [RoleRuleService.MAX_BLOCKS]
 * blocks, each with at least one and at most [RoleRuleService.MAX_CONDITIONS] conditions.
 */
@Serializable
data class RuleRequest(
    val roleId: String,
    val blocks: List<List<ConditionRequest>>,
)

/** Like [RuleRequest], for the role the rule has already. */
@Serializable
data class UpdateRuleRequest(
    val blocks: List<List<ConditionRequest>>,
)

/** Which of the optional fields are needed depends on [type]. */
@Serializable
data class ConditionRequest(
    val type: RuleType,
    val countryId: String? = null,
    /** For [RuleType.GOVERNMENT_OFFICE], at least one; holding any of them is enough. */
    val offices: List<Office> = emptyList(),
    val muId: String? = null,
    val threshold: Int? = null,
    val partyId: String? = null,
    val muRole: MuRole? = null,
)

/**
 * Part of the dashboard API, so the names stay as they are; [ConditionType] may change with the database. The
 * dashboard spells them out in `frontend/src/lib/api.ts`.
 */
@Serializable
enum class RuleType {
    CITIZENSHIP,
    GOVERNMENT_OFFICE,
    MILITARY_UNIT,
    LEVEL,
    MILITARY_RANK,
    COMPANIES,
    PARTY,
    MU_LEADERSHIP,
    PARTY_LEADER,
}

val RuleType.condition: ConditionType
    get() =
        when (this) {
            RuleType.CITIZENSHIP -> ConditionType.CITIZENSHIP
            RuleType.GOVERNMENT_OFFICE -> ConditionType.GOVERNMENT_OFFICE
            RuleType.MILITARY_UNIT -> ConditionType.MILITARY_UNIT
            RuleType.LEVEL -> ConditionType.LEVEL
            RuleType.MILITARY_RANK -> ConditionType.MILITARY_RANK
            RuleType.COMPANIES -> ConditionType.COMPANIES
            RuleType.PARTY -> ConditionType.PARTY
            RuleType.MU_LEADERSHIP -> ConditionType.MU_LEADERSHIP
            RuleType.PARTY_LEADER -> ConditionType.PARTY_LEADER
        }

val ConditionType.ruleType: RuleType
    get() =
        when (this) {
            ConditionType.CITIZENSHIP -> RuleType.CITIZENSHIP
            ConditionType.GOVERNMENT_OFFICE -> RuleType.GOVERNMENT_OFFICE
            ConditionType.MILITARY_UNIT -> RuleType.MILITARY_UNIT
            ConditionType.LEVEL -> RuleType.LEVEL
            ConditionType.MILITARY_RANK -> RuleType.MILITARY_RANK
            ConditionType.COMPANIES -> RuleType.COMPANIES
            ConditionType.PARTY -> RuleType.PARTY
            ConditionType.MU_LEADERSHIP -> RuleType.MU_LEADERSHIP
            ConditionType.PARTY_LEADER -> RuleType.PARTY_LEADER
        }

/** Like [RuleType], the API's names for [GovernmentOffice]. */
@Serializable
enum class Office {
    PRESIDENT,
    VICE_PRESIDENT,
    MINISTER_OF_ECONOMY,
    MINISTER_OF_DEFENSE,
    MINISTER_OF_FOREIGN_AFFAIRS,
    CONGRESS,
}

val Office.office: GovernmentOffice
    get() =
        when (this) {
            Office.PRESIDENT -> GovernmentOffice.PRESIDENT
            Office.VICE_PRESIDENT -> GovernmentOffice.VICE_PRESIDENT
            Office.MINISTER_OF_ECONOMY -> GovernmentOffice.MINISTER_OF_ECONOMY
            Office.MINISTER_OF_DEFENSE -> GovernmentOffice.MINISTER_OF_DEFENSE
            Office.MINISTER_OF_FOREIGN_AFFAIRS -> GovernmentOffice.MINISTER_OF_FOREIGN_AFFAIRS
            Office.CONGRESS -> GovernmentOffice.CONGRESS
        }

val GovernmentOffice.apiOffice: Office
    get() =
        when (this) {
            GovernmentOffice.PRESIDENT -> Office.PRESIDENT
            GovernmentOffice.VICE_PRESIDENT -> Office.VICE_PRESIDENT
            GovernmentOffice.MINISTER_OF_ECONOMY -> Office.MINISTER_OF_ECONOMY
            GovernmentOffice.MINISTER_OF_DEFENSE -> Office.MINISTER_OF_DEFENSE
            GovernmentOffice.MINISTER_OF_FOREIGN_AFFAIRS -> Office.MINISTER_OF_FOREIGN_AFFAIRS
            GovernmentOffice.CONGRESS -> Office.CONGRESS
        }

/** Like [RuleType], the API's names for [MilitaryUnitRole]. */
@Serializable
enum class MuRole {
    COMMANDER,
    MANAGER,
    OWNER,
}

val MuRole.role: MilitaryUnitRole
    get() =
        when (this) {
            MuRole.COMMANDER -> MilitaryUnitRole.COMMANDER
            MuRole.MANAGER -> MilitaryUnitRole.MANAGER
            MuRole.OWNER -> MilitaryUnitRole.OWNER
        }

val MilitaryUnitRole.apiRole: MuRole
    get() =
        when (this) {
            MilitaryUnitRole.COMMANDER -> MuRole.COMMANDER
            MilitaryUnitRole.MANAGER -> MuRole.MANAGER
            MilitaryUnitRole.OWNER -> MuRole.OWNER
        }

@Serializable
data class OptionResponse(
    val id: String,
    val name: String,
)
