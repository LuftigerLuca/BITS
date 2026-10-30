package eu.luftiger.bits.warera

/** Read access to the WarEra API. */
interface WareraClient {
    fun getAllCountries(): List<WareraCountry>

    /** IDs of the users whose name matches [text]; WarEra returns only the best few candidates. */
    fun searchUserIds(text: String): List<String>

    fun getUser(userId: String): WareraUser

    /**
     * The users with [userIds], each either loaded or failed on its own, e.g. with [WareraNotFoundException] for a user
     * WarEra no longer knows. Clients that can, load many users with one request.
     *
     * @throws WareraApiException if WarEra cannot be reached at all
     */
    fun getUsers(userIds: Collection<String>): Map<String, Result<WareraUser>> =
        userIds.distinct().associateWith { id ->
            try {
                Result.success(getUser(id))
            } catch (e: WareraNotFoundException) {
                Result.failure(e)
            } catch (e: WareraBadRequestException) {
                Result.failure(e)
            } catch (e: WareraSchemaException) {
                Result.failure(e)
            }
        }

    /** IDs of all companies owned by the user, across all pages. */
    fun getCompanyIds(userId: String): List<String>

    fun getCompany(companyId: String): WareraCompany

    fun getGovernment(countryId: String): WareraGovernment

    /** IDs of the military units whose name matches [text]; like [searchUserIds], only the best few candidates. */
    fun searchMilitaryUnitIds(text: String): List<String>

    fun getMilitaryUnit(muId: String): WareraMilitaryUnit

    /** All parties of the country with their members, across all pages. */
    fun getParties(countryId: String): List<WareraParty>
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
