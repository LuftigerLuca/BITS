package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.embassy.domain.EmbassyHostRole
import eu.luftiger.bits.backend.embassy.domain.EmbassyManualMember
import eu.luftiger.bits.backend.embassy.domain.EmbassyOffice
import eu.luftiger.bits.backend.embassy.domain.EmbassySetting
import eu.luftiger.bits.backend.embassy.domain.EmbassyThread
import eu.luftiger.bits.backend.members.api.GuildMembers
import eu.luftiger.bits.backend.overview.api.OverviewCountry
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/**
 * The embassy page of the dashboard, admins only. Every change is recorded in the audit log in its transaction and
 * starts an embassy sync once committed, so it shows in Discord right away.
 */
@Service
class EmbassyService(
    private val threadRepository: EmbassyThreadRepository,
    private val discordThreadRepository: DiscordThreadRepository,
    private val manualMemberRepository: EmbassyManualMemberRepository,
    private val threadMemberRepository: EmbassyThreadMemberRepository,
    private val officeRepository: EmbassyOfficeRepository,
    private val hostRoleRepository: EmbassyHostRoleRepository,
    private val settingRepository: EmbassySettingRepository,
    private val guildRoles: GuildRoles,
    private val guildMembers: GuildMembers,
    private val overviewCountry: OverviewCountry,
    private val wareraClient: WareraClient,
    private val auditLog: AuditLog,
    private val syncTrigger: EmbassySyncTrigger,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    fun overview(): EmbassiesResponse {
        val assigned = threadRepository.findAll().sortedBy { it.countryName }
        val reported = discordThreadRepository.findAll().associateBy { it.id }
        val manual = manualMemberRepository.findAll().groupBy { it.threadId }
        val members = threadMemberRepository.findAll().groupBy { it.threadId }
        val names = memberNames()
        return EmbassiesResponse(
            ownCountryId = overviewCountry.selected()?.id,
            settings = settings(),
            roles = guildRoles.all().map { RoleOption(it.id, it.name, it.color) },
            threads =
                assigned.map { thread ->
                    val current = reported[thread.threadId]
                    EmbassyThreadResponse(
                        threadId = thread.threadId,
                        threadName = current?.name ?: thread.threadName,
                        channelName = current?.parentChannelName,
                        countryId = thread.countryId,
                        countryName = thread.countryName,
                        missing = current == null,
                        members =
                            members[thread.threadId]
                                .orEmpty()
                                .sortedBy { it.name.lowercase() }
                                .map { ThreadMemberResponse(it.discordUserId, it.name, it.reason) },
                        manualMembers =
                            manual[thread.threadId]
                                .orEmpty()
                                .map { MemberOption(it.discordUserId, names[it.discordUserId] ?: it.memberName) }
                                .sortedBy { (it.name ?: it.discordUserId).lowercase() },
                    )
                },
            availableThreads =
                reported.values
                    .filter { it.id !in assigned.map(EmbassyThread::threadId) }
                    .sortedWith(compareBy({ it.parentChannelName.lowercase() }, { it.name.lowercase() }))
                    .map { ThreadOption(it.id, it.name, it.parentChannelName, it.archived, it.manageable) },
        )
    }

    /** Up to [MAX_SEARCH_RESULTS] members whose name contains [query] or whose ID is [query]; empty until the bot reported them. */
    fun searchMembers(query: String): List<MemberOption> {
        val text = query.trim().lowercase()
        if (text.isEmpty()) return emptyList()
        return guildMembers
            .members()
            .orEmpty()
            .filter { it.discordUserId == text || it.name?.lowercase()?.contains(text) == true }
            .sortedBy { it.name?.lowercase() }
            .take(MAX_SEARCH_RESULTS)
            .map { MemberOption(it.discordUserId, it.name) }
    }

    /**
     * @throws ResponseStatusException 400 for a thread the bot has not reported, a country WarEra does not know or the
     * country under "Verwaltung"; 409 if the thread or the country has one already
     * @throws WareraApiException if WarEra cannot be reached, which the dashboard gets as 503
     */
    fun assign(
        threadId: String,
        countryId: String,
        actor: AuditActor,
    ) {
        val thread = discordThreadRepository.findByIdOrNull(threadId) ?: throw badRequest("Unknown thread $threadId")
        if (countryId == overviewCountry.selected()?.id) throw badRequest("Own country $countryId")
        val country = wareraClient.getAllCountries().firstOrNull { it.id == countryId } ?: throw badRequest("Unknown country $countryId")
        change {
            if (threadRepository.existsById(threadId) || threadRepository.existsByCountryId(countryId)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Thread or country assigned already")
            }
            threadRepository.save(EmbassyThread(thread.id, thread.name, thread.parentChannelId, country.id, country.name, clock.instant()))
            auditLog.record(actor, AuditAction.EMBASSY_THREAD_ASSIGNED, "Botschafts-Thread ${thread.name}: ${country.name}")
        }
    }

    /** The thread stays as it is in Discord; BITS just stops managing it. @throws ResponseStatusException 404 */
    fun unassign(
        threadId: String,
        actor: AuditActor,
    ) = change {
        val thread = threadRepository.findByIdOrNull(threadId) ?: throw notFound()
        threadRepository.delete(thread)
        auditLog.record(
            actor,
            AuditAction.EMBASSY_THREAD_UNASSIGNED,
            "Botschafts-Thread ${thread.threadName} von ${thread.countryName} aufgehoben",
        )
    }

    /** @throws ResponseStatusException 404 for an unknown thread, 400 for a malformed ID, 409 if added already */
    fun addMember(
        threadId: String,
        discordUserId: String,
        actor: AuditActor,
    ) {
        if (!DISCORD_ID.matches(discordUserId)) throw badRequest("Malformed Discord ID")
        val name = memberNames()[discordUserId]
        change {
            val thread = threadRepository.findByIdOrNull(threadId) ?: throw notFound()
            if (manualMemberRepository.findByThreadIdAndDiscordUserId(threadId, discordUserId) != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Added already")
            }
            manualMemberRepository.save(EmbassyManualMember(threadId, discordUserId, name, clock.instant()))
            auditLog.record(
                actor,
                AuditAction.EMBASSY_MEMBER_ADDED,
                "${name ?: discordUserId} manuell zum Botschafts-Thread von ${thread.countryName} hinzugefügt",
            )
        }
    }

    /** Only members added by hand; whoever is in for their office stays. @throws ResponseStatusException 404 */
    fun removeMember(
        threadId: String,
        discordUserId: String,
        actor: AuditActor,
    ) {
        val name = memberNames()[discordUserId]
        change {
            val thread = threadRepository.findByIdOrNull(threadId) ?: throw notFound()
            val member = manualMemberRepository.findByThreadIdAndDiscordUserId(threadId, discordUserId) ?: throw notFound()
            manualMemberRepository.delete(member)
            auditLog.record(
                actor,
                AuditAction.EMBASSY_MEMBER_REMOVED,
                "${name ?: member.memberName ?: discordUserId} aus dem Botschafts-Thread von ${thread.countryName} entfernt",
            )
        }
    }

    /** @throws ResponseStatusException 400 for roles the bot has not reported */
    fun setSettings(
        request: EmbassySettingsRequest,
        actor: AuditActor,
    ) {
        val roles = guildRoles.all().associateBy { it.id }
        val roleIds = request.hostRoleIds.distinct() + listOfNotNull(request.ambassadorRoleId)
        val unknown = roleIds.filter { it !in roles }
        if (unknown.isNotEmpty()) throw badRequest("Unknown roles $unknown")
        val offices = request.offices.distinct().sortedBy { it.ordinal }
        change {
            officeRepository.deleteAllInBatch()
            officeRepository.saveAll(offices.map(::EmbassyOffice))
            hostRoleRepository.deleteAllInBatch()
            hostRoleRepository.saveAll(request.hostRoleIds.distinct().map(::EmbassyHostRole))
            val setting = settingRepository.findByIdOrNull(EmbassySetting.SINGLE_ROW_ID)
            if (setting == null) {
                settingRepository.save(EmbassySetting(request.ambassadorRoleId, clock.instant()))
            } else {
                setting.ambassadorRoleId = request.ambassadorRoleId
                setting.updatedAt = clock.instant()
            }
            val hostNames =
                request.hostRoleIds
                    .distinct()
                    .mapNotNull { roles[it]?.name }
                    .sorted()
            val description =
                listOf(
                    "Ämter: " + offices.joinToString { it.label }.ifEmpty { "keine" },
                    "Botschafter-Rolle: " + (request.ambassadorRoleId?.let { "@${roles.getValue(it).name}" } ?: "keine"),
                    "Gastgeber-Rollen: " + hostNames.joinToString { "@$it" }.ifEmpty { "keine" },
                ).joinToString("; ")
            auditLog.record(actor, AuditAction.EMBASSY_SETTINGS_CHANGED, "Botschaften: $description")
        }
    }

    private fun settings() =
        EmbassySettingsRequest(
            offices = officeRepository.findAll().map { it.office }.sortedBy { it.ordinal },
            ambassadorRoleId = settingRepository.findByIdOrNull(EmbassySetting.SINGLE_ROW_ID)?.ambassadorRoleId,
            hostRoleIds = hostRoleRepository.findAll().map { it.roleId }.sorted(),
        )

    private fun memberNames(): Map<String, String> =
        guildMembers
            .members()
            .orEmpty()
            .mapNotNull { member -> member.name?.let { member.discordUserId to it } }
            .toMap()

    /** Runs [block] in a transaction and syncs once it is committed. */
    private fun change(block: () -> Unit) {
        transactionTemplate.executeWithoutResult { block() }
        syncTrigger.request(SyncTrigger.SETTING_CHANGE)
    }

    private fun badRequest(reason: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND)

    private companion object {
        const val MAX_SEARCH_RESULTS = 20
        val DISCORD_ID = Regex("[0-9]{1,20}")
    }
}
