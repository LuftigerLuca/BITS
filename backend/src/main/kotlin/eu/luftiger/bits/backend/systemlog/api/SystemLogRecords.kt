package eu.luftiger.bits.backend.systemlog.api

import eu.luftiger.bits.backend.systemlog.api.dto.MemberLogRecords
import org.springframework.modulith.NamedInterface
import java.time.Instant

/** Cross-module boundary of the system log for reading it outside of the admin pages. */
@NamedInterface("api")
interface SystemLogRecords {
    /** When the bot last applied a sync of all members, hourly or after a rule change; `null` if none is in the log. */
    fun lastSyncOfAll(): Instant?

    /** What the log keeps about [discordUserId], without anything about other members. */
    fun recordsAbout(discordUserId: String): MemberLogRecords
}
