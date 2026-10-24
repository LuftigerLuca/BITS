package eu.luftiger.bits.warera

/** Read access to the WarEra API. */
interface WareraClient {
    fun getAllCountries(): List<WareraCountry>

    /** IDs of the users whose name matches [text]; WarEra returns only the best few candidates. */
    fun searchUserIds(text: String): List<String>

    fun getUser(userId: String): WareraUser

    /** IDs of all companies owned by the user, across all pages. */
    fun getCompanyIds(userId: String): List<String>

    fun getCompany(companyId: String): WareraCompany

    fun getGovernment(countryId: String): WareraGovernment

    /** IDs of the military units whose name matches [text]; like [searchUserIds], only the best few candidates. */
    fun searchMilitaryUnitIds(text: String): List<String>

    fun getMilitaryUnit(muId: String): WareraMilitaryUnit
}

open class WareraApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** WarEra answered that the requested object does not exist. */
class WareraNotFoundException(
    message: String,
    cause: Throwable? = null,
) : WareraApiException(message, cause)

/** WarEra rejected the input of a call, e.g. an ID in the wrong format. Not an outage. */
class WareraBadRequestException(
    message: String,
    cause: Throwable? = null,
) : WareraApiException(message, cause)

/** The answer of WarEra did not match what BITS expects; the unofficial API changes without notice. */
class WareraSchemaException(
    message: String,
) : WareraApiException(message)
