package eu.luftiger.bits.backend.ping.api

import org.springframework.modulith.NamedInterface
import java.time.Instant

/** Cross-module boundary of the ping module. */
@NamedInterface("api")
interface PingLogs {
    /** When [discordUserId] used `/ping`, oldest first, as far as the pings are kept. */
    fun pingsOf(discordUserId: String): List<Instant>
}
