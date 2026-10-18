package eu.luftiger.bits.warera

/** Read access to the WarEra API. */
interface WareraClient {
    fun getAllCountries(): List<WareraCountry>
}

class WareraApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
