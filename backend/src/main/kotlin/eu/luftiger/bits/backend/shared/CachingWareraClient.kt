package eu.luftiger.bits.backend.shared

import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCountry
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraMilitaryUnit
import eu.luftiger.bits.warera.WareraParty
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Keeps answers that hardly ever change for a while, since every WarEra call counts against the 100 per minute that
 * verification and role sync share: the countries, loaded for each `/ping`, country picker and country rule, the
 * military units, loaded for each search in the MU picker and each MU leadership rule, and the parties and the
 * government of each country, loaded for each `/profil`. Role sync, `/rollen` and the evaluation of votes therefore see
 * parties and governments up to [countryDetailsTtl] old, and military units up to [militaryUnitTtl]. Errors are not
 * kept.
 */
class CachingWareraClient(
    private val delegate: WareraClient,
    private val clock: Clock,
    private val countriesTtl: Duration = Duration.ofHours(1),
    private val militaryUnitTtl: Duration = Duration.ofMinutes(10),
    private val countryDetailsTtl: Duration = Duration.ofMinutes(10),
) : WareraClient by delegate {
    private var countries: Cached<List<WareraCountry>>? = null
    private val militaryUnits = HashMap<String, Cached<WareraMilitaryUnit>>()
    private val parties = HashMap<String, Cached<List<WareraParty>>>()
    private val governments = HashMap<String, Cached<WareraGovernment>>()

    @Synchronized
    override fun getAllCountries(): List<WareraCountry> {
        countries?.takeIf { it.isFresh() }?.let { return it.value }
        return delegate.getAllCountries().also { countries = Cached(it, clock.instant().plus(countriesTtl)) }
    }

    @Synchronized
    override fun getMilitaryUnit(muId: String): WareraMilitaryUnit = militaryUnits.load(muId, militaryUnitTtl, delegate::getMilitaryUnit)

    @Synchronized
    override fun getParties(countryId: String): List<WareraParty> = parties.load(countryId, countryDetailsTtl, delegate::getParties)

    @Synchronized
    override fun getGovernment(countryId: String): WareraGovernment =
        governments.load(countryId, countryDetailsTtl, delegate::getGovernment)

    private fun <T> HashMap<String, Cached<T>>.load(
        key: String,
        ttl: Duration,
        loader: (String) -> T,
    ): T {
        get(key)?.takeIf { it.isFresh() }?.let { return it.value }
        values.removeIf { !it.isFresh() }
        return loader(key).also { put(key, Cached(it, clock.instant().plus(ttl))) }
    }

    private inner class Cached<T>(
        val value: T,
        private val expiresAt: Instant,
    ) {
        fun isFresh() = clock.instant().isBefore(expiresAt)
    }
}
