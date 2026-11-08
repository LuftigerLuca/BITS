package eu.luftiger.bits.backend.rolesync.internal

import eu.luftiger.bits.backend.rolesync.api.MinisterTitleCheck
import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.backend.systemlog.api.SystemLog
import eu.luftiger.bits.backend.systemlog.api.SystemProblem
import eu.luftiger.bits.warera.WareraGovernment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/** Remembers the titles it reported in memory only, so after a restart each unknown title is reported once more. */
@Service
class MinisterTitleCheckService(
    private val systemLog: SystemLog,
) : MinisterTitleCheck {
    private val log = LoggerFactory.getLogger(javaClass)
    private val reported = ConcurrentHashMap.newKeySet<String>()

    override fun check(
        government: WareraGovernment,
        countryName: String,
    ) {
        val fresh = GovernmentOffice.unknownTitles(government).filter { reported.add(it) }
        if (fresh.isEmpty()) return
        log.warn("Government of {} lists ministers with unknown titles {}", countryName, fresh)
        systemLog.recordProblem(SystemProblem.UnknownMinisterTitles(countryName, fresh))
    }
}
