package eu.luftiger.bits.backend.profile.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.rolesync.api.GuildRoles
import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.shared.ProfileDetail
import eu.luftiger.bits.shared.ProfileParty
import eu.luftiger.bits.shared.ProfileResponse
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraNotFoundException
import eu.luftiger.bits.warera.WareraUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * The WarEra profile behind a link, for `/profil`. The user comes fresh from WarEra on every call, so the profile
 * matches what WarEra shows; the countries, military units, parties and governments come from the cache of the WarEra
 * client. The party, the offices and the companies are details: if WarEra cannot deliver one, the profile comes
 * without it; only a user that cannot be loaded fails the whole call.
 */
@Service
class ProfileService(
    private val accountLinks: AccountLinks,
    private val guildRoles: GuildRoles,
    private val wareraClient: WareraClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun profile(discordUserId: String): ProfileResponse {
        val link = accountLinks.findByDiscordUserId(discordUserId) ?: return ProfileResponse.NotLinked
        val user =
            try {
                wareraClient.getUser(link.wareraUserId)
            } catch (_: WareraNotFoundException) {
                return ProfileResponse.WareraUserNotFound
            }
        val countryName = user.country?.let { id -> wareraClient.getAllCountries().firstOrNull { it.id == id }?.name }
        val militaryUnitName =
            user.mu?.let {
                try {
                    wareraClient.getMilitaryUnit(it).name
                } catch (_: WareraNotFoundException) {
                    null
                }
            }
        val unavailable = mutableSetOf<ProfileDetail>()
        val party = detail(ProfileDetail.PARTY, user, unavailable) { party(user) }
        val offices = detail(ProfileDetail.OFFICES, user, unavailable) { offices(user) }
        val companyCount = detail(ProfileDetail.COMPANIES, user, unavailable) { wareraClient.getCompanyIds(user.id).size }
        return ProfileResponse.Found(
            wareraUserId = user.id,
            wareraUsername = user.username,
            level = user.leveling.level,
            militaryRank = user.militaryRank,
            countryName = countryName,
            militaryUnitName = militaryUnitName,
            profileUrl = PROFILE_URL + user.id,
            party = party,
            offices = offices.orEmpty(),
            companyCount = companyCount ?: 0,
            managedRoleIds = guildRoles.managedIds(),
            unavailable = unavailable,
        )
    }

    /** Only in the country of citizenship, like the evaluation of votes. */
    private fun party(user: WareraUser): ProfileParty? {
        val country = user.country ?: return null
        val party = wareraClient.getParties(country).firstOrNull { user.id in it.members } ?: return null
        return ProfileParty(party.name, leader = party.leader == user.id)
    }

    private fun offices(user: WareraUser): List<String> {
        val country = user.country ?: return emptyList()
        val government = wareraClient.getGovernment(country)
        return GovernmentOffice.entries.filter { it.isHeldBy(government, user.id) }.map { it.label }
    }

    /** `null` and listed in [unavailable] if WarEra cannot deliver it. */
    private fun <T> detail(
        detail: ProfileDetail,
        user: WareraUser,
        unavailable: MutableSet<ProfileDetail>,
        load: () -> T,
    ): T? =
        try {
            load()
        } catch (e: WareraApiException) {
            log.warn("{} of WarEra user {} could not be loaded, the profile comes without it", detail, user.id, e)
            unavailable += detail
            null
        }

    private companion object {
        const val PROFILE_URL = "https://app.warera.io/user/"
    }
}
