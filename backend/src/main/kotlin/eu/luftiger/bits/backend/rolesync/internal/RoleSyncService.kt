package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.account.api.dto.LinkedAccount
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.rolesync.domain.NicknameSetting
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraUser
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Works out the target roles of the members from the rules and the WarEra data of the linked accounts, and hands them
 * to the bot as a [RoleSyncEvent].
 *
 * Syncs that publish run one at a time: a sync of all members reads the links when it starts, so a sync of a single
 * member running in between could be overwritten with stale data. [refreshUsers] only loads data and may run alongside,
 * see [RoleSyncTrigger].
 *
 * Every sync is a run in the [SystemLog], written together with its event; members whose WarEra data is missing and
 * governments that cannot be loaded are recorded there as problems of the run.
 *
 * If admins turned it on, the event also carries the WarEra names the bot sets as nicknames, and for members who lost
 * their link the name to clear again. Then a sync runs even without rules.
 */
@Service
class RoleSyncService(
    private val ruleInputs: RuleInputs,
    private val nicknameSettings: NicknameSettingRepository,
    private val accountLinks: AccountLinks,
    private val wareraClient: WareraClient,
    private val eventPublisher: EventPublisher,
    private val systemLog: SystemLog,
    private val transactionTemplate: TransactionTemplate,
    private val properties: RoleSyncProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Result of the last lookup per WarEra user ID. A rule change only changes how the data is judged, so it reuses this
     * instead of loading every user again.
     */
    private val users = ConcurrentHashMap<String, Lookup>()

    /** `null` if the user could not be loaded; the member then keeps the roles as they are. */
    private class Lookup(
        val user: WareraUser?,
    )

    /** Loads the WarEra data of every linked user again, without syncing; [syncAll] uses it afterwards. */
    fun refreshUsers() {
        val links = accountLinks.findAll()
        users.keys.retainAll(links.mapTo(HashSet()) { it.wareraUserId })
        links.forEachIndexed { index, link -> loadUser(link.wareraUserId, pauseBefore = index > 0) }
        log.info("WarEra data of {} linked users refreshed", links.size)
    }

    /**
     * Syncs all members of the guild with the WarEra data loaded last; users not loaded yet are loaded now.
     * [confirmedRoleIds] are roles whose rules an admin changed, see [RoleSyncEvent.confirmedRoleIds].
     */
    @Synchronized
    fun syncAll(
        confirmedRoleIds: List<String> = emptyList(),
        trigger: SyncTrigger = SyncTrigger.HOURLY,
    ) = sync(accountLinks.findAll(), discordUserIds = null, refresh = false, trigger = trigger, confirmedRoleIds = confirmedRoleIds)

    /**
     * Syncs the given members only, with fresh WarEra data; members without a link lose all managed roles, and the
     * nickname of the account in [formerAccounts] they had.
     */
    @Synchronized
    fun syncMembers(
        discordUserIds: Collection<String>,
        formerAccounts: Map<String, FormerAccount> = emptyMap(),
    ) {
        val links = discordUserIds.mapNotNull { id -> accountLinks.findByDiscordUserId(id)?.let { LinkedAccount(id, it.wareraUserId) } }
        sync(
            links,
            discordUserIds = discordUserIds.distinct(),
            refresh = true,
            trigger = SyncTrigger.ACCOUNT_CHANGE,
            formerAccounts = formerAccounts,
        )
    }

    private fun sync(
        links: List<LinkedAccount>,
        discordUserIds: List<String>?,
        refresh: Boolean,
        trigger: SyncTrigger,
        confirmedRoleIds: List<String> = emptyList(),
        formerAccounts: Map<String, FormerAccount> = emptyMap(),
    ) {
        val rules = ruleInputs.activeRules()
        val nicknamesEnabled = nicknameSettings.findByIdOrNull(NicknameSetting.SINGLE_ROW_ID)?.enabled == true
        if (rules.isEmpty() && !nicknamesEnabled) {
            systemLog.recordSyncWithoutRules(trigger)
            return
        }
        val governments =
            ruleInputs.governments(rules) { countryId, countryName ->
                systemLog.recordProblem(SystemProblem.GovernmentUnavailable(countryId, countryName))
            }
        val evaluator = RuleEvaluator(rules, governments)
        var lookups = 0
        val unavailable = mutableListOf<String>()
        val members = HashMap<String, MemberRoles>()
        val nicknames = HashMap<String, String>()
        links.forEach { link ->
            val cached = users[link.wareraUserId]?.takeUnless { refresh }
            val user = if (cached != null) cached.user else loadUser(link.wareraUserId, pauseBefore = lookups++ > 0)
            if (user == null) unavailable += link.discordUserId else nicknames[link.discordUserId] = user.username
            evaluator.evaluate(user)?.let { members[link.discordUserId] = it }
        }
        if (unavailable.isNotEmpty()) systemLog.recordProblem(SystemProblem.WareraUsersUnavailable(unavailable))
        val linkedIds = links.mapTo(HashSet()) { it.discordUserId }
        // The nickname was set from the data loaded last, which may be newer than the name at the time of linking.
        val nicknameResets =
            formerAccounts
                .filterKeys { it !in linkedIds }
                .mapNotNull { (discordUserId, account) ->
                    (users[account.wareraUserId]?.user?.username ?: account.wareraUsername)?.let { discordUserId to it }
                }.toMap()
        transactionTemplate.executeWithoutResult {
            val syncId = systemLog.recordSyncRun(trigger, links.size)
            eventPublisher.publish(
                RoleSyncEvent(
                    managedRoleIds = rules.map { it.roleId }.distinct(),
                    discordUserIds = discordUserIds,
                    members = members,
                    occurredAt = clock.millis(),
                    confirmedRoleIds = confirmedRoleIds,
                    syncId = syncId,
                    nicknames = nicknames.takeIf { nicknamesEnabled },
                    nicknameResets = if (nicknamesEnabled) nicknameResets else emptyMap(),
                ),
            )
        }
        log.info("Role sync published for {} members, {} linked", discordUserIds?.size ?: "all", links.size)
    }

    /** WarEra account a member was linked to until now, for clearing the nickname taken from it. */
    data class FormerAccount(
        val wareraUserId: String,
        /** Name at the time of linking, in case the account's data is not loaded. */
        val wareraUsername: String?,
    )

    private fun loadUser(
        wareraUserId: String,
        pauseBefore: Boolean,
    ): WareraUser? {
        if (pauseBefore && !properties.userLookupInterval.isZero) Thread.sleep(properties.userLookupInterval)
        val user =
            try {
                wareraClient.getUser(wareraUserId)
            } catch (e: WareraApiException) {
                log.warn("WarEra user {} could not be loaded, their roles stay as they are", wareraUserId, e)
                null
            }
        users[wareraUserId] = Lookup(user)
        return user
    }
}
