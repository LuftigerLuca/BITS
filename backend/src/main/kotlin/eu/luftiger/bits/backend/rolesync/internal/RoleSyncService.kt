package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.account.api.NicknameOptIns
import eu.luftiger.bits.backend.account.api.dto.LinkedAccount
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.feature.api.BitsFeature
import eu.luftiger.bits.backend.feature.api.FeatureToggles
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.backend.systemlog.api.SyncTrigger
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.shared.MemberRoles
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.shared.StatusRoles
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraUser
import org.slf4j.LoggerFactory
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
 * governments, parties or military units that cannot be loaded are recorded there as problems of the run.
 *
 * The companies of a user cost a call of their own, so they are loaded only while a rule has a company condition. All
 * calls per user are paced by [RoleSyncProperties.userLookupInterval].
 *
 * The event also carries the WarEra names the bot sets as nicknames of members who opted in, and for those who lost
 * their link the name to clear again. With opt-ins, a sync runs even without rules.
 *
 * Besides the rules, the DSKs give their roles to the members of their military units; that needs only the users.
 * The status roles need no WarEra data at all: whoever has a link is verified, see [StatusRoleLookup].
 *
 * A sync leaves out what an admin switched off: without the role sync it has no rules and DSKs, so the bot changes no
 * roles; without nicknames nobody counts as opted in, so the bot sets and clears none; without the verification, it
 * leaves the status roles alone.
 */
@Service
class RoleSyncService(
    private val ruleInputs: RuleInputs,
    private val statusRoleLookup: StatusRoleLookup,
    private val nicknameOptIns: NicknameOptIns,
    private val features: FeatureToggles,
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
    private val users = ConcurrentHashMap<String, LoadedUser>()

    @Volatile
    private var loadedAll = false

    /**
     * Whether the data of all linked users was loaded since the backend started; until then, [lastLoadedUsers] misses
     * many of them, since it is kept in memory only.
     */
    fun loadedAll(): Boolean = loadedAll

    /** Back to the state after a start of the backend, for tests. */
    internal fun forget() {
        users.clear()
        loadedAll = false
    }

    /**
     * The WarEra data a rule change would be judged with now, by WarEra user ID. A user who is missing would be loaded
     * by that sync first; a `null` value could not be loaded.
     */
    fun lastLoadedUsers(): Map<String, LoadedUser> = HashMap(users)

    /** Loads the WarEra data of every linked user again, without syncing; [syncAll] uses it afterwards. */
    fun refreshUsers() {
        val links = accountLinks.findAll()
        val withCompanies = needsCompanies(ruleInputs.activeRules())
        users.keys.retainAll(links.mapTo(HashSet()) { it.wareraUserId })
        val pace = Pace()
        links.forEach { link -> load(link.wareraUserId, withCompanies, pace) }
        loadedAll = true
        log.info("WarEra data of {} linked users refreshed", links.size)
    }

    /**
     * Syncs all members of the guild with the WarEra data loaded last; users not loaded yet are loaded now.
     * [confirmedRoleIds] are roles whose rules an admin changed, see [RoleSyncEvent.confirmedRoleIds]. [revokedRoleIds]
     * are roles BITS no longer manages after such a change, e.g. of a deleted DSK: this sync still counts them as managed
     * and gives them to nobody, so the bot takes them from everybody, unless a rule or DSK gives them meanwhile.
     */
    @Synchronized
    fun syncAll(
        confirmedRoleIds: List<String> = emptyList(),
        trigger: SyncTrigger = SyncTrigger.HOURLY,
        revokedRoleIds: List<String> = emptyList(),
    ) = sync(
        accountLinks.findAll(),
        discordUserIds = null,
        refresh = false,
        trigger = trigger,
        confirmedRoleIds = confirmedRoleIds,
        revokedRoleIds = revokedRoleIds,
    )

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
        revokedRoleIds: List<String> = emptyList(),
    ) {
        val roleSync = features.isEnabled(BitsFeature.ROLE_SYNC)
        val rules = if (roleSync) ruleInputs.activeRules() else emptyList()
        val dsks = if (roleSync) ruleInputs.activeDskRoles() else emptyList()
        val revoked = if (roleSync) revokedRoleIds else emptyList()
        val optedIn = if (features.isEnabled(BitsFeature.NICKNAMES)) nicknameOptIns.discordUserIds() else emptySet()
        val statusRoles = statusRoleLookup.active()
        if (rules.isEmpty() && dsks.isEmpty() && revoked.isEmpty() && optedIn.isEmpty() && statusRoles == null) {
            systemLog.recordSyncWithoutRules(trigger)
            return
        }
        val withCompanies = needsCompanies(rules)
        val pace = Pace()
        val needsUsers = rules.isNotEmpty() || dsks.isNotEmpty() || optedIn.isNotEmpty()
        val loadedUsers =
            (if (needsUsers) links else emptyList()).associateWith { link ->
                val cached = users[link.wareraUserId]?.takeUnless { refresh }
                when {
                    cached == null -> load(link.wareraUserId, withCompanies, pace)
                    // A company rule was added since the user was loaded.
                    withCompanies && cached.user != null && !cached.companiesLoaded -> loadCompanies(cached, pace)
                    else -> cached
                }
            }
        // The users first, since a leadership rule for any military unit needs the units of the members.
        val memberMuIds = if (ruleInputs.needsMemberMilitaryUnits(rules)) loadedUsers.values.mapNotNull { it.user?.mu } else emptyList()
        val evaluator = RuleEvaluator(rules, ruleInputs.ruleData(rules, memberMuIds, systemLog::recordProblem), dsks)
        val unavailable = mutableListOf<String>()
        val members = HashMap<String, MemberRoles>()
        val nicknames = HashMap<String, String>()
        loadedUsers.forEach { (link, loaded) ->
            val user = loaded.user
            if (user == null || (withCompanies && loaded.companyCount == null)) unavailable += link.discordUserId
            if (user != null && link.discordUserId in optedIn) nicknames[link.discordUserId] = user.username
            evaluator.evaluate(user, loaded.companyCount)?.let { members[link.discordUserId] = it }
        }
        if (discordUserIds == null && needsUsers) loadedAll = true
        if (unavailable.isNotEmpty()) systemLog.recordProblem(SystemProblem.WareraUsersUnavailable(unavailable))
        val linkedIds = links.mapTo(HashSet()) { it.discordUserId }
        // The nickname was set from the data loaded last, which may be newer than the name at the time of linking.
        val nicknameResets =
            formerAccounts
                .filterKeys { it !in linkedIds && it in optedIn }
                .mapNotNull { (discordUserId, account) ->
                    (users[account.wareraUserId]?.user?.username ?: account.wareraUsername)?.let { discordUserId to it }
                }.toMap()
        transactionTemplate.executeWithoutResult {
            val syncId = systemLog.recordSyncRun(trigger, links.size)
            eventPublisher.publish(
                RoleSyncEvent(
                    managedRoleIds = (rules.map { it.roleId } + dsks.map { it.roleId } + revoked).distinct(),
                    discordUserIds = discordUserIds,
                    members = members,
                    occurredAt = clock.millis(),
                    confirmedRoleIds = confirmedRoleIds,
                    syncId = syncId,
                    nicknames = nicknames,
                    nicknameResets = nicknameResets,
                    statusRoles =
                        statusRoles?.let {
                            StatusRoles(it.verifiedRoleId, it.unverifiedRoleId, linkedIds.sorted())
                        },
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

    private fun needsCompanies(rules: List<RoleRule>) = rules.any { rule -> rule.conditions.any { it.type == ConditionType.COMPANIES } }

    /** The user, and with [withCompanies] their companies too. */
    private fun load(
        wareraUserId: String,
        withCompanies: Boolean,
        pace: Pace,
    ): LoadedUser {
        pace.next()
        val user =
            try {
                wareraClient.getUser(wareraUserId)
            } catch (e: WareraApiException) {
                log.warn("WarEra user {} could not be loaded, their roles stay as they are", wareraUserId, e)
                null
            }
        val loaded = LoadedUser(wareraUserId, user)
        users[wareraUserId] = loaded
        return if (withCompanies && user != null) loadCompanies(loaded, pace) else loaded
    }

    private fun loadCompanies(
        loaded: LoadedUser,
        pace: Pace,
    ): LoadedUser {
        pace.next()
        val count =
            try {
                wareraClient.getCompanyIds(loaded.wareraUserId).size
            } catch (e: WareraApiException) {
                log.warn("Companies of WarEra user {} could not be loaded, their company conditions are undecided", loaded.wareraUserId, e)
                null
            }
        return LoadedUser(loaded.wareraUserId, loaded.user, count, companiesLoaded = true).also { users[loaded.wareraUserId] = it }
    }

    /** Pauses before every WarEra call of a sync but the first. */
    private inner class Pace {
        private var first = true

        fun next() {
            if (!first && !properties.userLookupInterval.isZero) Thread.sleep(properties.userLookupInterval)
            first = false
        }
    }
}

/** The WarEra data of a user as a sync loaded it last. */
class LoadedUser(
    val wareraUserId: String,
    /** `null` if the user could not be loaded; the member then keeps the roles as they are. */
    val user: WareraUser?,
    /** `null` if not loaded, see [companiesLoaded], or if the companies could not be loaded. */
    val companyCount: Int? = null,
    /** Whether the companies were loaded with the user, which happens only while a rule needs them. */
    val companiesLoaded: Boolean = false,
)
