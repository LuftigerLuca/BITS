package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.account.api.dto.LinkedAccount
import eu.luftiger.bits.backend.event.api.EventPublisher
import eu.luftiger.bits.backend.rolesync.domain.ConditionType
import eu.luftiger.bits.backend.rolesync.domain.RoleRule
import eu.luftiger.bits.shared.RoleSyncEvent
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Works out the target roles of the members from the rules and the WarEra data of the linked accounts, and hands them
 * to the bot as a [RoleSyncEvent].
 *
 * Syncs that publish run one at a time: a sync of all members reads the links when it starts, so a sync of a single
 * member running in between could be overwritten with stale data. [refreshUsers] only loads data and may run alongside,
 * see [RoleSyncTrigger].
 */
@Service
class RoleSyncService(
    private val ruleRepository: RoleRuleRepository,
    private val roleRepository: DiscordRoleRepository,
    private val accountLinks: AccountLinks,
    private val wareraClient: WareraClient,
    private val eventPublisher: EventPublisher,
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
    fun syncAll(confirmedRoleIds: List<String> = emptyList()) =
        sync(accountLinks.findAll(), discordUserIds = null, refresh = false, confirmedRoleIds = confirmedRoleIds)

    /** Syncs the given members only, with fresh WarEra data; members without a link lose all managed roles. */
    @Synchronized
    fun syncMembers(discordUserIds: Collection<String>) {
        val links = discordUserIds.mapNotNull { id -> accountLinks.findByDiscordUserId(id)?.let { LinkedAccount(id, it.wareraUserId) } }
        sync(links, discordUserIds = discordUserIds.distinct(), refresh = true)
    }

    private fun sync(
        links: List<LinkedAccount>,
        discordUserIds: List<String>?,
        refresh: Boolean,
        confirmedRoleIds: List<String> = emptyList(),
    ) {
        val rules = activeRules()
        if (rules.isEmpty()) return
        val evaluator = RuleEvaluator(rules, governments(rules))
        var lookups = 0
        val members =
            links
                .mapNotNull { link ->
                    val cached = users[link.wareraUserId]?.takeUnless { refresh }
                    val user = if (cached != null) cached.user else loadUser(link.wareraUserId, pauseBefore = lookups++ > 0)
                    evaluator.evaluate(user)?.let { link.discordUserId to it }
                }.toMap()
        eventPublisher.publish(
            RoleSyncEvent(
                managedRoleIds = rules.map { it.roleId }.distinct(),
                discordUserIds = discordUserIds,
                members = members,
                occurredAt = clock.millis(),
                confirmedRoleIds = confirmedRoleIds,
            ),
        )
        log.info("Role sync published for {} members, {} linked", discordUserIds?.size ?: "all", links.size)
    }

    /** Rules whose role exists and can be given by the bot; orphaned rules are not run. */
    private fun activeRules(): List<RoleRule> {
        val assignable = roleRepository.findAll().filter { it.assignable }.mapTo(HashSet()) { it.id }
        return ruleRepository.findAll().filter { it.roleId in assignable }
    }

    /** One call per country that office rules refer to. A country whose government cannot be loaded is left out. */
    private fun governments(rules: List<RoleRule>): Map<String, WareraGovernment> =
        rules
            .filter { it.type == ConditionType.GOVERNMENT_OFFICE }
            .mapNotNull { it.countryId }
            .distinct()
            .mapNotNull { countryId ->
                try {
                    countryId to wareraClient.getGovernment(countryId)
                } catch (e: WareraApiException) {
                    log.warn("Government of country {} could not be loaded, its office rules are skipped", countryId, e)
                    null
                }
            }.toMap()

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
