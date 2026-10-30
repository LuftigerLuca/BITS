package eu.luftiger.bits.backend.shared

import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCountry
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraMilitaryUnit
import eu.luftiger.bits.warera.WareraParty
import eu.luftiger.bits.warera.WareraUser
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps answers that hardly ever change for a while, since every WarEra call counts against the 100 per minute that
 * verification and role sync share: the countries, loaded for each `/ping`, country picker and country rule, the
 * military units, loaded for each search in the MU picker and each MU leadership rule, and the parties and the
 * government of each country, loaded for each `/profil`. Role sync, `/rollen` and the evaluation of votes therefore see
 * parties and governments up to [countryDetailsTtl] old, and military units up to [militaryUnitTtl]. Users are kept for
 * [userTtl] only, so that `/profil`, `/rollen` and the dashboard called again and again cost one call, while a member
 * who changed something in WarEra sees it in BITS after a short while. Errors are not kept.
 *
 * A hit never waits. A miss waits only for a load of the same key, which may take seconds behind the rate limiter, so
 * that one key is loaded once however many callers ask at the same time.
 */
class CachingWareraClient(
    private val delegate: WareraClient,
    private val clock: Clock,
    private val countriesTtl: Duration = Duration.ofHours(1),
    private val militaryUnitTtl: Duration = Duration.ofMinutes(10),
    private val countryDetailsTtl: Duration = Duration.ofMinutes(10),
    private val userTtl: Duration = Duration.ofMinutes(2),
) : WareraClient by delegate {
    private val countries = Cache<List<WareraCountry>>(countriesTtl)
    private val militaryUnits = Cache<WareraMilitaryUnit>(militaryUnitTtl)
    private val parties = Cache<List<WareraParty>>(countryDetailsTtl)
    private val governments = Cache<WareraGovernment>(countryDetailsTtl)
    private val users = Cache<WareraUser>(userTtl)

    override fun getAllCountries(): List<WareraCountry> = countries.load(ALL) { delegate.getAllCountries() }

    override fun getMilitaryUnit(muId: String): WareraMilitaryUnit = militaryUnits.load(muId, delegate::getMilitaryUnit)

    override fun getParties(countryId: String): List<WareraParty> = parties.load(countryId, delegate::getParties)

    override fun getGovernment(countryId: String): WareraGovernment = governments.load(countryId, delegate::getGovernment)

    override fun getUser(userId: String): WareraUser = users.load(userId, delegate::getUser)

    /** The users cached are served from the cache, the others loaded with one call to the delegate. */
    override fun getUsers(userIds: Collection<String>): Map<String, Result<WareraUser>> {
        val cached = userIds.distinct().associateWith { users.fresh(it) }
        val missing = cached.filterValues { it == null }.keys
        val loaded = if (missing.isEmpty()) emptyMap() else delegate.getUsers(missing)
        loaded.forEach { (id, result) -> result.onSuccess { users.put(id, it) } }
        return cached.mapValues { (id, user) -> if (user != null) Result.success(user) else loaded.getValue(id) }
    }

    private inner class Cache<T>(
        private val ttl: Duration,
    ) {
        private val entries = ConcurrentHashMap<String, Cached<T>>()

        /** One lock per key, so a slow load blocks only the callers of the same key. */
        private val locks = ConcurrentHashMap<String, Any>()

        fun load(
            key: String,
            loader: (String) -> T,
        ): T {
            fresh(key)?.let { return it }
            synchronized(locks.computeIfAbsent(key) { Any() }) {
                // Another caller may have loaded it while this one waited.
                fresh(key)?.let { return it }
                entries.values.removeIf { !it.isFresh() }
                return loader(key).also { entries[key] = Cached(it, clock.instant().plus(ttl)) }
            }
        }

        fun fresh(key: String): T? = entries[key]?.takeIf { it.isFresh() }?.value

        fun put(
            key: String,
            value: T,
        ) {
            entries[key] = Cached(value, clock.instant().plus(ttl))
        }
    }

    private inner class Cached<T>(
        val value: T,
        private val expiresAt: Instant,
    ) {
        fun isFresh() = clock.instant().isBefore(expiresAt)
    }

    private companion object {
        const val ALL = "all"
    }
}
