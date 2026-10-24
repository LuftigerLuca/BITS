package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StartVerificationRequest(
    val discordUserId: String,
    val wareraUsername: String,
)

/** Outcome of starting a verification. Each subtype is identified by its `type` field. */
@Serializable
sealed interface StartVerificationResponse {
    /** A new code was issued; any code the user had before is no longer valid. */
    @Serializable
    @SerialName("started")
    data class Started(
        val code: String,
        /** WarEra username as spelled in WarEra. */
        val wareraUsername: String,
        /** Epoch milliseconds after which the code is no longer accepted. */
        val expiresAt: Long,
        /** WarEra username the Discord account is linked to right now, if any; replaced once the new link succeeds. */
        val linkedWareraUsername: String?,
    ) : StartVerificationResponse

    @Serializable
    @SerialName("user_not_found")
    data object UserNotFound : StartVerificationResponse

    /** The code has to go into a company name, so a user without a company cannot verify. */
    @Serializable
    @SerialName("no_company")
    data class NoCompany(
        val wareraUsername: String,
    ) : StartVerificationResponse
}

@Serializable
data class CheckVerificationRequest(
    val discordUserId: String,
    val code: String,
    /** Name of the member in the guild, for the backend's system log; `null` from a bot before it. */
    val discordName: String? = null,
)

/**
 * Outcome of checking a verification code. Each subtype is identified by its `type` field. The answer to the request
 * is either [Checking] or one of the outcomes that need no WarEra call; the others follow in a
 * [VerificationCheckedEvent].
 */
@Serializable
sealed interface CheckVerificationResponse {
    /** The code is being looked for in the company names; WarEra can take a while, so the result follows as event. */
    @Serializable
    @SerialName("checking")
    data object Checking : CheckVerificationResponse

    @Serializable
    @SerialName("linked")
    data class Linked(
        val wareraUsername: String,
    ) : CheckVerificationResponse

    /** WarEra could not be reached during the check; the code stays valid. */
    @Serializable
    @SerialName("warera_unavailable")
    data object WareraUnavailable : CheckVerificationResponse

    /** None of the user's company names contains the code yet; the code stays valid. */
    @Serializable
    @SerialName("code_missing")
    data object CodeMissing : CheckVerificationResponse

    @Serializable
    @SerialName("expired")
    data object Expired : CheckVerificationResponse

    /** The code is not the user's current one, e.g. because it was used already or replaced by a newer one. */
    @Serializable
    @SerialName("invalid")
    data object Invalid : CheckVerificationResponse

    /** Checks are throttled to spare the WarEra API. */
    @Serializable
    @SerialName("too_early")
    data class TooEarly(
        val retryAfterSeconds: Long,
    ) : CheckVerificationResponse
}

@Serializable
data class UnlinkAccountRequest(
    val discordUserId: String,
)

/** Answer to `/unverify`; the link and any open code of the user are gone either way. */
@Serializable
data class UnlinkAccountResponse(
    /** WarEra username the Discord account was linked to, or `null` if it was not linked. */
    val wareraUsername: String?,
)
