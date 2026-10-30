package eu.luftiger.bits.backend.militaryunit.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.auth.api.SessionPrincipal
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

/** The page "Militäreinheiten" of the dashboard. Only for admins, see the security config of `/web/admin/`. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/web/admin/military-units")
class DskController(
    private val dskService: DskService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun overview(): MilitaryUnitsResponse = dskService.overview()

    @PostMapping("/dsks")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: DskRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        dskService.create(request, actor(session))
        log.info("DSK {} created by {}", request.name, session.subject)
    }

    /** Renames the DSK and sets its role. */
    @PutMapping("/dsks/{dskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun update(
        @PathVariable dskId: Long,
        @RequestBody request: DskRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        dskService.update(dskId, request, actor(session))
        log.info("DSK {} updated by {}", dskId, session.subject)
    }

    @DeleteMapping("/dsks/{dskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable dskId: Long,
        @AuthenticationPrincipal session: Jwt,
    ) {
        dskService.delete(dskId, actor(session))
        log.info("DSK {} deleted by {}", dskId, session.subject)
    }

    /** Military units of all countries, by name. */
    @GetMapping("/search")
    fun search(
        @RequestParam query: String,
    ): List<MilitaryUnitOption> = dskService.searchMilitaryUnits(query)

    @PostMapping("/dsks/{dskId}/military-units")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMilitaryUnit(
        @PathVariable dskId: Long,
        @RequestBody request: AddMilitaryUnitRequest,
        @AuthenticationPrincipal session: Jwt,
    ) {
        dskService.addMilitaryUnit(dskId, request.muId, actor(session))
        log.info("Military unit {} added to DSK {} by {}", request.muId, dskId, session.subject)
    }

    @DeleteMapping("/dsks/{dskId}/military-units/{muId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMilitaryUnit(
        @PathVariable dskId: Long,
        @PathVariable muId: String,
        @AuthenticationPrincipal session: Jwt,
    ) {
        dskService.removeMilitaryUnit(dskId, muId, actor(session))
        log.info("Military unit {} removed from DSK {} by {}", muId, dskId, session.subject)
    }

    /** Only when searching or assigning a military unit. */
    private fun actor(session: Jwt) = SessionPrincipal.of(session).let { AuditActor(it.subject, it.name, it.avatarUrl) }
}

@Serializable
data class MilitaryUnitsResponse(
    /** The roles the bot can give, highest first. */
    val roles: List<DskRoleOption>,
    /** By name. */
    val dsks: List<DskResponse>,
)

/** A role for a DSK; only free if it has no rule, is no status role and belongs to no other DSK. */
@Serializable
data class DskRoleOption(
    val id: String,
    val name: String,
    val color: Int?,
    val hasRule: Boolean,
    /** Whether it is the role for verified or unverified members. */
    val statusRole: Boolean,
    /** The DSK the role belongs to, `null` if none. */
    val dskId: Long?,
)

@Serializable
data class DskResponse(
    val id: Long,
    val name: String,
    /** `null` if the DSK has no role; it then changes nothing. */
    val roleId: String?,
    /** `null` without a role or if it was deleted in Discord. */
    val roleName: String?,
    val roleColor: Int?,
    /** `null` without a role. */
    val roleStatus: DskRoleStatus?,
    /** By name, as of the time they were assigned. */
    val militaryUnits: List<MilitaryUnitResponse>,
)

@Serializable
enum class DskRoleStatus {
    ACTIVE,

    /** The role was deleted in Discord; nobody gets it. */
    ORPHANED,

    /** The role exists, but the bot cannot give it, e.g. because it is above the bot's highest role. */
    NOT_ASSIGNABLE,
}

@Serializable
data class MilitaryUnitResponse(
    val id: String,
    val name: String,
)

@Serializable
data class MilitaryUnitOption(
    val id: String,
    val name: String,
    /** Name of the country the unit is based in, its ID if WarEra does not list it, `null` if WarEra names none. */
    val countryName: String?,
    /** The DSK the unit belongs to already, `null` if none; it cannot be assigned to another. */
    val dskName: String?,
)

@Serializable
data class DskRequest(
    val name: String,
    /** `null` for none. */
    val roleId: String? = null,
)

@Serializable
data class AddMilitaryUnitRequest(
    val muId: String,
)
