package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.GovernmentOffice
import eu.luftiger.bits.warera.WareraApiException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
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
@RequestMapping("/web/admin/role-sync")
class RoleRuleController(
    private val ruleService: RoleRuleService,
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
    ): RuleResponse = ruleService.create(request)

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) = ruleService.delete(id)

    @GetMapping("/countries")
    fun countries(): List<OptionResponse> = ruleService.countries()

    @GetMapping("/military-units")
    fun militaryUnits(
        @RequestParam query: String,
    ): List<OptionResponse> = ruleService.searchMilitaryUnits(query)

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

/** Which of the optional fields are needed depends on [type]. */
@Serializable
data class CreateRuleRequest(
    val roleId: String,
    val type: ConditionType,
    val countryId: String? = null,
    val office: GovernmentOffice? = null,
    val muId: String? = null,
    val threshold: Int? = null,
)

@Serializable
data class OptionResponse(
    val id: String,
    val name: String,
)
