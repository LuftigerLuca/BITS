package eu.luftiger.bits.warera

import java.time.Duration

/**
 * Learns about the calls to WarEra, e.g. for statistics. Called on the calling thread, so an implementation must be
 * quick and must not throw.
 */
interface WareraCallListener {
    /**
     * One HTTP request to [procedure], each of which counts against WarEra's limit, retries included. [status] is
     * `null` if no answer came, e.g. after a timeout; [permitWait] is how long the request waited for the rate limiter.
     */
    fun requestSent(
        procedure: String,
        status: Int?,
        permitWait: Duration,
        duration: Duration,
    ) {}

    /** A call to [procedure] is tried once more after an error; the request follows with [requestSent]. */
    fun retried(procedure: String) {}

    /** A call to [procedure] gave up, since the rate limiter had no permit for it in time; nothing was sent. */
    fun throttled(procedure: String) {}

    /** A call to [procedure] that a cache in front of the client answered ([hit]) or passed on. */
    fun cacheLookup(
        procedure: String,
        hit: Boolean,
    ) {}

    companion object {
        val NONE = object : WareraCallListener {}
    }
}

/** The tRPC procedures of WarEra that [RestWareraClient] calls. */
object WareraProcedure {
    const val ALL_COUNTRIES = "country.getAllCountries"
    const val USER = "user.getUserLite"
    const val COMPANIES = "company.getCompanies"
    const val COMPANY = "company.getById"
    const val GOVERNMENT = "government.getByCountryId"
    const val MILITARY_UNIT = "mu.getById"
    const val PARTIES = "party.getManyPaginated"
    const val SEARCH = "search.searchAnything"
}
