package eu.luftiger.bits.backend.ping

import eu.luftiger.bits.backend.events.EventPublisher
import eu.luftiger.bits.shared.PingEvent
import eu.luftiger.bits.shared.PingResponse
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock

/** Example flow through all parts: database, WarEra API and an event for the bot. */
@Service
class PingService(
    private val repository: PingLogRepository,
    private val wareraClient: WareraClient,
    private val eventPublisher: EventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ping(discordUserId: String): PingResponse {
        val now = clock.instant()
        repository.save(PingLog(discordUserId = discordUserId, createdAt = now))

        val countryCount =
            try {
                wareraClient.getAllCountries().size
            } catch (e: WareraApiException) {
                log.warn("WarEra API not reachable", e)
                null
            }

        eventPublisher.publish(PingEvent(discordUserId = discordUserId, occurredAt = now.toEpochMilli()))
        return PingResponse(pingCount = repository.count(), wareraCountryCount = countryCount)
    }
}
