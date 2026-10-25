package eu.luftiger.bits.backend.account.api.dto

import org.springframework.modulith.NamedInterface
import java.time.Instant

/** What the account module keeps about one Discord account; each part is `null` if there is none. */
@NamedInterface("api")
data class StoredAccountData(
    val link: StoredLink?,
    val verification: StoredVerification?,
    val nicknameOptedInAt: Instant?,
)

@NamedInterface("api")
data class StoredLink(
    val wareraUserId: String,
    /** Username at the time of linking, like [LinkedWareraAccount.wareraUsername]. */
    val wareraUsername: String,
    val verifiedAt: Instant,
)

/** A code the member has not put into a company name yet. */
@NamedInterface("api")
data class StoredVerification(
    val wareraUserId: String,
    val wareraUsername: String,
    val code: String,
    val expiresAt: Instant,
)
