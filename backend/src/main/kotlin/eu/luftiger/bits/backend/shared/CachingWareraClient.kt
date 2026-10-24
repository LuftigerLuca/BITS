package eu.luftiger.bits.backend.shared

import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCountry
import eu.luftiger.bits.warera.WareraMilitaryUnit
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Keeps answers that hardly ever change for a while, since every WarEra call counts against the 100 per minute that
 * verification and role sync share: the countries, loaded for each `/ping`, country picker and country rule, and the
 * military units, loaded for each search in the MU picker. Errors are not kept.
 */
class CachingWareraClient(
    private val delegate: WareraClient,
    private val clock: Clock,
    private val countriesTtl: Duration = Duration.ofHours(1),
    private val militaryUnitTtl: Duration = Duration.ofMinutes(10),
) : WareraClient by delegate {
    private var countries: Cached<List<WareraCountry>>? = null
    private val militaryUnits = HashMap<String, Cached<WareraMilitaryUnit>>()

    @Synchronized
    override fun getAllCountries(): List<WareraCountry> {
        countries?.takeIf { it.isFresh() }?.let { return it.value }
        return delegate.getAllCountries().also { countries = Cached(it, clock.instant().plus(countriesTtl)) }
    }

    @Synchronized
    override fun getMilitaryUnit(muId: String): WareraMilitaryUnit {
        militaryUnits[muId]?.takeIf { it.isFresh() }?.let { return it.value }
        militaryUnits.values.removeIf { !it.isFresh() }
        return delegate.getMilitaryUnit(muId).also { militaryUnits[muId] = Cached(it, clock.instant().plus(militaryUnitTtl)) }
    }

    private inner class Cached<T>(
        val value: T,
        private val expiresAt: Instant,
    ) {
        fun isFresh() = clock.instant().isBefore(expiresAt)
    }
}
