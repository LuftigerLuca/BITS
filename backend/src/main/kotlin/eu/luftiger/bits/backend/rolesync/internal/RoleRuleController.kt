package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.GovernmentOffice
import eu.luftiger.bits.warera.WareraApiException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
        @RequestBody request: CreateRuleRequest,
        @AuthenticationPrincipal session: Jwt,
    ): RuleResponse {
        val rule = ruleService.create(request, actor(session))
        log.info("Rule {} ({}) for role {} created by {}", rule.id, rule.label, rule.roleId, session.subject)
        return rule
    }

    /** What creating the rule would change, without creating it. */
    @PostMapping("/rules/preview")
    fun previewCreate(
        @RequestBody request: CreateRuleRequest,
    ): RulePreviewResponse = previewService.previewCreate(request)

    /** What deleting the rule would change, without deleting it. */
    @GetMapping("/rules/{id}/deletion-preview")
    fun previewDelete(
        @PathVariable id: Long,
    ): RulePreviewResponse = previewService.previewDelete(id)

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
        @AuthenticationPrincipal session: Jwt,
    ) {
        val rule = ruleService.delete(id, actor(session))
        log.info("Rule {} ({}) for role {} deleted by {}", id, rule.label, rule.roleId, session.subject)
    }

    @GetMapping("/countries")
    fun countries(): List<OptionResponse> = ruleService.countries()

    @GetMapping("/military-units")
    fun militaryUnits(
        @RequestParam query: String,
    ): List<OptionResponse> = ruleService.searchMilitaryUnits(query)

    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }

    @ExceptionHandler(WareraApiException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun wareraUnavailable(e: WareraApiException) {
        log.warn("WarEra API not reachable", e)
    }
}

@Serializable
data class RoleResponse(
    val id: String,
    val name: String,
    val color: Int?,
    /** Whether a rule exists for the role already, i.e. BITS manages it. */
    val managed: Boolean,
)

@Serializable
data class RuleResponse(
    val id: Long,
    val roleId: String,
    /** `null` if the role was deleted in Discord. */
    val roleName: String?,
    val roleColor: Int?,
    val label: String,
    val status: RuleStatus,
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
    /** `false` if no active rule is left for the role: BITS no longer manages it, and nobody gets or loses it. */
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

/** Which of the optional fields are needed depends on [type]. */
@Serializable
data class CreateRuleRequest(
    val roleId: String,
    val type: RuleType,
    val countryId: String? = null,
    val office: Office? = null,
    val muId: String? = null,
    val threshold: Int? = null,
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
}

val RuleType.condition: ConditionType
    get() =
        when (this) {
            RuleType.CITIZENSHIP -> ConditionType.CITIZENSHIP
            RuleType.GOVERNMENT_OFFICE -> ConditionType.GOVERNMENT_OFFICE
            RuleType.MILITARY_UNIT -> ConditionType.MILITARY_UNIT
            RuleType.LEVEL -> ConditionType.LEVEL
            RuleType.MILITARY_RANK -> ConditionType.MILITARY_RANK
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

@Serializable
data class OptionResponse(
    val id: String,
    val name: String,
)
