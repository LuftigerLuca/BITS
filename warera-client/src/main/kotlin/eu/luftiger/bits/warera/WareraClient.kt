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
}

class WareraApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
