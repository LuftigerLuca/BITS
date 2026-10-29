package eu.luftiger.bits.backend.militaryunit.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.militaryunit.domain.Dsk
import eu.luftiger.bits.backend.militaryunit.domain.DskMilitaryUnit
import eu.luftiger.bits.backend.overview.api.OverviewCountry
import eu.luftiger.bits.backend.overview.api.dto.SelectedCountry
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.rolesync.api.RoleSyncRequests
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
 * The page "Militäreinheiten" of the dashboard, admins only: the DSKs, their roles and their military units.
 *
 * A role belongs either to one DSK or to a rule of the role sync, never to both. Military units can only be assigned if
 * they belong to the country under "Verwaltung", which is checked once, when assigning.
 *
 * Every change is recorded in the audit log in its transaction. A change that affects who gets a role syncs all members
 * once committed, and confirms the role like a rule change, so the bot takes it from as many members as it takes. A role
 * the DSK loses, with its deletion or a new role, is taken from everybody by that sync.
 * WarEra calls run outside of transactions, like everywhere else.
 */
@Service
class DskService(
    private val dskRepository: DskRepository,
    private val militaryUnitRepository: DskMilitaryUnitRepository,
    private val guildRoles: GuildRoles,
    private val overviewCountry: OverviewCountry,
    private val wareraClient: WareraClient,
    private val auditLog: AuditLog,
    private val syncRequests: RoleSyncRequests,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    fun overview(): MilitaryUnitsResponse {
        val dsks = dskRepository.findAll().sortedBy { it.name.lowercase() }
        val units = militaryUnitRepository.findAll().groupBy { it.dskId }
        val roles = guildRoles.all().associateBy { it.id }
        val assignable = guildRoles.assignable()
        val assignableIds = assignable.mapTo(HashSet()) { it.id }
        val withRule = guildRoles.withRuleIds()
        val dskByRole = dsks.filter { it.roleId != null }.associateBy { requireNotNull(it.roleId) }
        return MilitaryUnitsResponse(
            country = overviewCountry.selected()?.let { CountryOption(it.id, it.name) },
            roles =
                assignable.map {
                    DskRoleOption(it.id, it.name, it.color, hasRule = it.id in withRule, dskId = dskByRole[it.id]?.id)
                },
            dsks =
                dsks.map { dsk ->
                    val role = dsk.roleId?.let { roles[it] }
                    DskResponse(
                        id = requireNotNull(dsk.id),
                        name = dsk.name,
                        roleId = dsk.roleId,
                        roleName = role?.name,
                        roleColor = role?.color,
                        roleStatus =
                            when {
                                dsk.roleId == null -> null
                                role == null -> DskRoleStatus.ORPHANED
                                dsk.roleId !in assignableIds -> DskRoleStatus.NOT_ASSIGNABLE
                                else -> DskRoleStatus.ACTIVE
                            },
                        militaryUnits =
                            units[dsk.id]
                                .orEmpty()
                                .sortedBy { it.muName.lowercase() }
                                .map { MilitaryUnitResponse(it.muId, it.muName) },
                    )
                },
        )
    }

    /** @throws ResponseStatusException 400 if the name or the role is invalid, see [validRole]; 409 if either is taken */
    fun create(
        request: DskRequest,
        actor: AuditActor,
    ) {
        val name = validName(request.name)
        val roleId = request.roleId?.let(::validRole)
        transactionTemplate.executeWithoutResult {
            if (dskRepository.existsByNameIgnoreCase(name)) throw conflict("DSK $name exists already")
            if (roleId != null && dskRepository.findByRoleId(roleId) != null) throw conflict("Role $roleId belongs to another DSK")
            saveAndFlush(Dsk(name, roleId, clock.instant()))
            auditLog.record(actor, AuditAction.DSK_CREATED, "$name, ${roleLabel(roleId)}")
        }
        roleId?.let { syncRequests.syncAfterChange(listOf(it)) }
    }

    /**
     * Renames the DSK and sets its role, `null` for none.
     *
     * @throws ResponseStatusException 404 if the DSK does not exist; 400 and 409 like [create]
     */
    fun update(
        dskId: Long,
        request: DskRequest,
        actor: AuditActor,
    ) {
        val name = validName(request.name)
        val current = dskRepository.findByIdOrNull(dskId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        // The role it has may be gone in Discord meanwhile, which must not keep the DSK from being renamed.
        val roleId = request.roleId?.let { if (it == current.roleId) it else validRole(it) }
        val formerRoleId =
            transactionTemplate.execute {
                val dsk = dskRepository.findByIdOrNull(dskId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                if (!name.equals(dsk.name, ignoreCase = true) && dskRepository.existsByNameIgnoreCase(name)) {
                    throw conflict("DSK $name exists already")
                }
                if (roleId != null && dskRepository.findByRoleId(roleId).let { it != null && it.id != dskId }) {
                    throw conflict("Role $roleId belongs to another DSK")
                }
                val before = "${dsk.name}, ${roleLabel(dsk.roleId)}"
                val formerRoleId = dsk.roleId
                dsk.name = name
                dsk.roleId = roleId
                saveAndFlush(dsk)
                auditLog.record(actor, AuditAction.DSK_UPDATED, "$name, ${roleLabel(roleId)} (vorher: $before)")
                formerRoleId
            }
        // A new name only changes the reason the bot gives from now on.
        if (formerRoleId != roleId) syncRequests.syncAfterChange(listOfNotNull(roleId), revokedRoleIds = listOfNotNull(formerRoleId))
    }

    /**
     * Deletes the DSK with its military units; its role is no longer managed and taken from everybody.
     *
     * @throws ResponseStatusException 404 if the DSK does not exist
     */
    fun delete(
        dskId: Long,
        actor: AuditActor,
    ) {
        val roleId =
            transactionTemplate.execute {
                val dsk = dskRepository.findByIdOrNull(dskId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                val units = militaryUnitRepository.findByDskId(dskId)
                militaryUnitRepository.deleteAllInBatch(units)
                dskRepository.delete(dsk)
                val unitNames = if (units.isEmpty()) "" else ", MUs: ${units.sortedBy { it.muName.lowercase() }.joinToString { it.muName }}"
                auditLog.record(actor, AuditAction.DSK_DELETED, "${dsk.name}, ${roleLabel(dsk.roleId)}$unitNames")
                dsk.roleId
            }
        roleId?.let { syncRequests.syncAfterChange(emptyList(), revokedRoleIds = listOf(it)) }
    }

    /**
     * Military units of the country under "Verwaltung" whose name matches [query]. WarEra's search returns the best few
     * matches of all countries, each of which needs a call of its own; those of other countries are left out.
     *
     * @throws ResponseStatusException 400 if no country is set
     */
    fun searchMilitaryUnits(query: String): List<MilitaryUnitOption> {
        val country = selectedCountry()
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val assigned = militaryUnitRepository.findAll().associate { it.muId to it.dskId }
        val names = dskRepository.findAll().associate { it.id to it.name }
        return wareraClient
            .searchMilitaryUnitIds(text)
            .take(MAX_SEARCH_RESULTS)
            .map(wareraClient::getMilitaryUnit)
            .filter { it.country == country.id }
            .map { MilitaryUnitOption(it.id, it.name, dskName = assigned[it.id]?.let(names::get)) }
    }

    /**
     * @throws ResponseStatusException 400 if no country is set, or WarEra does not know the unit or it belongs to another
     * country; 404 if the DSK does not exist; 409 if the unit belongs to a DSK already
     */
    fun addMilitaryUnit(
        dskId: Long,
        muId: String,
        actor: AuditActor,
    ) {
        val country = selectedCountry()
        val mu = knownMilitaryUnit(muId)
        if (mu.country != country.id) throw badRequest("Military unit $muId does not belong to ${country.name}")
        val roleId =
            transactionTemplate.execute {
                val dsk = dskRepository.findByIdOrNull(dskId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                if (militaryUnitRepository.existsById(mu.id)) throw conflict("Military unit ${mu.id} belongs to a DSK already")
                try {
                    militaryUnitRepository.saveAndFlush(DskMilitaryUnit(mu.id, dskId, mu.name, clock.instant()))
                } catch (_: DataIntegrityViolationException) {
                    // If another admin was faster.
                    throw conflict("Military unit ${mu.id} belongs to a DSK already")
                }
                auditLog.record(actor, AuditAction.DSK_MILITARY_UNIT_ADDED, "MU ${mu.name} zu ${dsk.name}")
                dsk.roleId
            }
        roleId?.let { syncRequests.syncAfterChange(listOf(it)) }
    }

    /** @throws ResponseStatusException 404 if the unit does not belong to the DSK */
    fun removeMilitaryUnit(
        dskId: Long,
        muId: String,
        actor: AuditActor,
    ) {
        val roleId =
            transactionTemplate.execute {
                val unit =
                    militaryUnitRepository.findByIdOrNull(muId)?.takeIf { it.dskId == dskId }
                        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
                val dsk = dskRepository.getReferenceById(dskId)
                militaryUnitRepository.delete(unit)
                auditLog.record(actor, AuditAction.DSK_MILITARY_UNIT_REMOVED, "MU ${unit.muName} aus ${dsk.name}")
                dsk.roleId
            }
        roleId?.let { syncRequests.syncAfterChange(listOf(it)) }
    }

    private fun saveAndFlush(dsk: Dsk) {
        try {
            dskRepository.saveAndFlush(dsk)
        } catch (_: DataIntegrityViolationException) {
            // The unique indexes on the name and the role, if another admin was faster.
            throw conflict("Name or role of the DSK taken")
        }
    }

    private fun validName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) throw badRequest("Name must have 1 to $MAX_NAME_LENGTH characters")
        return trimmed
    }

    /**
     * @throws ResponseStatusException 400 if the bot cannot give the role; 409 if it has a rule of the role sync, which
     * gives it instead
     */
    private fun validRole(roleId: String): String {
        if (guildRoles.assignable().none { it.id == roleId }) throw badRequest("Role $roleId cannot be assigned")
        if (roleId in guildRoles.withRuleIds()) throw conflict("Role $roleId has a rule")
        return roleId
    }

    private fun selectedCountry(): SelectedCountry = overviewCountry.selected() ?: throw badRequest("No country selected")

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

    /** For the audit log, readable after the role is gone, e.g. "Rolle @DSK1". */
    private fun roleLabel(roleId: String?): String {
        if (roleId == null) return "keine Rolle"
        val name = guildRoles.all().firstOrNull { it.id == roleId }?.name
        return "Rolle ${name?.let { "@$it" } ?: "$roleId (in Discord gelöscht)"}"
    }

    private fun conflict(reason: String) = ResponseStatusException(HttpStatus.CONFLICT, reason)

    private fun badRequest(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    private companion object {
        const val MAX_NAME_LENGTH = 100

        /** More than for the rules, since those of other countries are left out. */
        const val MAX_SEARCH_RESULTS = 10
    }
}
