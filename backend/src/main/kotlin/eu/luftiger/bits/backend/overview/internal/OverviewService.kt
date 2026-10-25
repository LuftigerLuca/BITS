package eu.luftiger.bits.backend.overview.internal

import eu.luftiger.bits.backend.account.api.AccountLinks
import eu.luftiger.bits.backend.audit.api.AuditAction
import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.audit.api.AuditLog
import eu.luftiger.bits.backend.members.api.GuildMembers
import eu.luftiger.bits.backend.overview.domain.OverviewSetting
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/**
 * The country's figures come from WarEra's country list, which the [WareraClient] keeps for an hour, so they may be
 * that old. WarEra calls run outside of transactions, like everywhere else.
 */
@Service
class OverviewService(
    private val settingRepository: OverviewSettingRepository,
    private val guildMembers: GuildMembers,
    private val accountLinks: AccountLinks,
    private val wareraClient: WareraClient,
    private val auditLog: AuditLog,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun overview(): OverviewResponse {
        val members = guildMembers.discordUserIds()
        // Only links of current members, so a link the reconciliation has not caught yet is not counted.
        val verified = members?.let { ids -> accountLinks.findAll().count { it.discordUserId in ids } }
        return OverviewResponse(members?.size, verified, settingRepository.findByIdOrNull(OverviewSetting.SINGLE_ROW_ID)?.let(::country))
    }

    /**
     * @throws ResponseStatusException 400 if WarEra does not know the country
     * @throws WareraApiException if WarEra cannot be reached, which the dashboard gets as 503
     */
    fun setCountry(
        countryId: String,
        actor: AuditActor,
    ): CountryResponse {
        val country =
            wareraClient.getAllCountries().firstOrNull { it.id == countryId }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown country $countryId")
        transactionTemplate.executeWithoutResult {
            val setting = settingRepository.findByIdOrNull(OverviewSetting.SINGLE_ROW_ID)
            if (setting == null) {
                settingRepository.save(OverviewSetting(country.id, country.name, clock.instant()))
            } else {
                setting.countryId = country.id
                setting.countryName = country.name
                setting.updatedAt = clock.instant()
            }
            auditLog.record(actor, AuditAction.OVERVIEW_COUNTRY_SET, "Land der Startseite: ${country.name}")
        }
        return CountryResponse(country.id, country.name)
    }

    private fun country(setting: OverviewSetting): CountryOverview {
        val country =
            try {
                wareraClient.getAllCountries().firstOrNull { it.id == setting.countryId }
            } catch (e: WareraApiException) {
                log.warn("WarEra API not reachable, the overview shows no citizens", e)
                null
            }
        return CountryOverview(
            id = setting.countryId,
            name = country?.name ?: setting.countryName,
            available = country != null,
            citizens = country?.currentPopulation,
            activeCitizens = country?.activePopulation,
        )
    }
}
