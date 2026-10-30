package eu.luftiger.bits.backend.embassy.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.embassy.domain.EmbassySetting
import eu.luftiger.bits.backend.embassy.domain.EmbassyThread
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.rolesync.api.MemberCountries
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.shared.EmbassySyncEvent
import eu.luftiger.bits.shared.EmbassyThreadTarget
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraGovernment
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Works out who belongs in each embassy thread and hands it to the bot as an [EmbassySyncEvent]: the linked holders of
 * the chosen offices in the thread's country, and the members added by hand. The members of the host roles are added
 * by the bot, which knows who holds a role right now.
 *
 * Only the governments of countries with a thread are loaded, outside of any transaction. The [WareraClient] keeps them
 * for a few minutes, so a sync right after another one costs no calls. A country whose government cannot be loaded is
 * recorded in the [SystemLog]; its thread then loses nobody, see [EmbassyThreadTarget.complete].
 *
 * First, every sync asks the bot for the threads of foreign countries in which a linked member holds a chosen office,
 * see [EmbassyThreadCreationService.createMissing]. The countries of the members come from the role sync; those of
 * newly linked members are loaded right away.
 */
@Service
class EmbassySyncService(
    private val threadRepository: EmbassyThreadRepository,
    private val manualMemberRepository: EmbassyManualMemberRepository,
    private val officeRepository: EmbassyOfficeRepository,
    private val hostRoleRepository: EmbassyHostRoleRepository,
    private val settingRepository: EmbassySettingRepository,
    private val accountLinks: AccountLinks,
    private val memberCountries: MemberCountries,
    private val creationService: EmbassyThreadCreationService,
    private val wareraClient: WareraClient,
    private val eventPublisher: EventPublisher,
    private val systemLog: SystemLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [newWareraUserIds] are users linked just now, whose country is loaded unless the role sync knows it. Publishes
     * nothing while no thread is assigned, not even a log entry, so an unused feature costs nothing.
     */
    fun sync(
        trigger: SyncTrigger,
        newWareraUserIds: Set<String> = emptySet(),
    ) {
        createMissingThreads(newWareraUserIds)
        val threads = threadRepository.findAll().sortedBy { it.countryName }
        if (threads.isEmpty()) return
        val offices = officeRepository.findAll().map { it.office }.sortedBy { it.ordinal }
        val governments = threads.distinctBy { it.countryId }.associate { it.countryId to government(it) }
        val links = accountLinks.findAll()
        val manual = manualMemberRepository.findAll().groupBy { it.threadId }
        val targets =
            threads.map { thread ->
                val members = LinkedHashMap<String, String>()
                governments[thread.countryId]?.let { government ->
                    offices.forEach { office ->
                        links
                            .filter { office.isHeldBy(government, it.wareraUserId) }
                            .forEach { members.putIfAbsent(it.discordUserId, "${office.label} von ${thread.countryName}") }
                    }
                }
                manual[thread.threadId].orEmpty().forEach { members.putIfAbsent(it.discordUserId, MANUAL_REASON) }
                EmbassyThreadTarget(
                    threadId = thread.threadId,
                    parentChannelId = thread.parentChannelId,
                    countryName = thread.countryName,
                    members = members,
                    complete = governments[thread.countryId] != null,
                )
            }
        val ambassadors = LinkedHashMap<String, String>()
        targets.forEach { target ->
            target.members.forEach { (discordUserId, reason) ->
                val qualified = if (reason == MANUAL_REASON) "Manuell im Thread von ${target.countryName}" else reason
                ambassadors.putIfAbsent(discordUserId, "BITS: $qualified")
            }
        }
        val ambassadorRoleId = settingRepository.findByIdOrNull(EmbassySetting.SINGLE_ROW_ID)?.ambassadorRoleId
        val hostRoleIds = hostRoleRepository.findAll().map { it.roleId }.sorted()
        transactionTemplate.executeWithoutResult {
            val syncId = systemLog.recordEmbassySyncRun(trigger, threads.size)
            eventPublisher.publish(
                EmbassySyncEvent(
                    syncId = syncId,
                    threads = targets,
                    hostRoleIds = hostRoleIds,
                    ambassadorRoleId = ambassadorRoleId,
                    ambassadors = ambassadors,
                    occurredAt = clock.millis(),
                ),
            )
        }
        log.info("Embassy sync published for {} threads, {} ambassadors", threads.size, ambassadors.size)
    }

    /** Costs no WarEra call for newly linked users while no embassy channel is set. */
    private fun createMissingThreads(newWareraUserIds: Set<String>) {
        if (settingRepository.findByIdOrNull(EmbassySetting.SINGLE_ROW_ID)?.channelId == null) return
        val linked = accountLinks.findAll().mapTo(HashSet()) { it.wareraUserId }
        val countries = memberCountries.byWareraUserId().filterKeys { it in linked }.toMutableMap()
        val missing = newWareraUserIds.filter { it in linked && it !in countries }
        if (missing.isNotEmpty()) {
            try {
                wareraClient.getUsers(missing).forEach { (id, result) ->
                    result
                        .onSuccess { user -> user.country?.let { countries[id] = it } }
                        .onFailure { e ->
                            log.warn("WarEra user {} could not be loaded, their country gets no embassy thread for now", id, e)
                        }
                }
            } catch (e: WareraApiException) {
                log.warn("{} WarEra users could not be loaded, their countries get no embassy thread for now", missing.size, e)
            }
        }
        creationService.createMissing(countries)
    }

    private fun government(thread: EmbassyThread): WareraGovernment? =
        try {
            wareraClient.getGovernment(thread.countryId)
        } catch (e: WareraApiException) {
            log.warn("Government of country {} could not be loaded, its embassy thread loses nobody", thread.countryId, e)
            systemLog.recordProblem(SystemProblem.EmbassyGovernmentUnavailable(thread.countryId, thread.countryName))
            null
        }

    companion object {
        const val MANUAL_REASON = "Manuell hinzugefügt"
    }
}
