package eu.luftiger.bits.backend.profile.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.shared.ProfileResponse
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraNotFoundException
import org.springframework.stereotype.Service

/**
 * The WarEra profile behind a link, for `/profil`. The user comes fresh from WarEra on every call, so the profile
 * matches what WarEra shows; the countries and military units come from the cache of the WarEra client.
 */
@Service
class ProfileService(
    private val accountLinks: AccountLinks,
    private val wareraClient: WareraClient,
) {
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
        return ProfileResponse.Found(
            wareraUserId = user.id,
            wareraUsername = user.username,
            level = user.leveling.level,
            militaryRank = user.militaryRank,
            countryName = countryName,
            militaryUnitName = militaryUnitName,
            profileUrl = PROFILE_URL + user.id,
        )
    }

    private companion object {
        const val PROFILE_URL = "https://app.warera.io/user/"
    }
}
