package eu.luftiger.bits.backend.account.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

@NamedInterface("api")
data class AccountRecord(
    val discordUserId: String,
    val wareraUserId: String,
    /** Username at the time of linking; WarEra users can rename themselves. */
    val wareraUsername: String,
    val verifiedAt: Instant,
)
