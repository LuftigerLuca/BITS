package eu.luftiger.bits.backend.externalapi.internal

import eu.luftiger.bits.backend.shared.WebInput
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/*
 * The endpoints of the third-party API. Each names its scope with [ExternalEndpoint]; the paths must match
 * [ExternalScope.path]. Their types are the API's own and part of its contract: fields may be added, but existing ones
 * keep their names and meaning within /v1.
 */

@RestController
@RequestMapping("/external/v1/accounts")
@Tag(name = "Accounts", description = "Verknüpfungen von Discord- und WarEra-Accounts, die Mitglieder per /verify bestätigt haben.")
class ExternalAccountController(
    private val service: ExternalReadService,
) {
    @GetMapping
    @ExternalEndpoint(ExternalScope.ACCOUNTS_LIST)
    fun accounts(
        @Parameter(description = "Seite, ab 0") @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) page: Int,
        @Parameter(description = "Einträge pro Seite, 1 bis 100")
        @RequestParam(defaultValue = "${ExternalReadService.DEFAULT_PAGE_SIZE}")
        @Min(1)
        @Max(ExternalReadService.MAX_PAGE_SIZE)
        size: Int,
    ): PageResponse<AccountResponse> = service.accounts(page, size)

    @GetMapping("/discord/{discordUserId}")
    @ExternalEndpoint(ExternalScope.ACCOUNTS_BY_DISCORD_ID)
    fun byDiscordId(
        @PathVariable @Size(max = WebInput.MAX_ID_LENGTH) discordUserId: String,
    ): AccountResponse = service.accountByDiscordId(discordUserId)

    @GetMapping("/warera/{wareraUserId}")
    @ExternalEndpoint(ExternalScope.ACCOUNTS_BY_WARERA_ID)
    fun byWareraId(
        @PathVariable @Size(max = WebInput.MAX_WARERA_ID_LENGTH) wareraUserId: String,
    ): AccountResponse = service.accountByWareraId(wareraUserId)
}

@RestController
@RequestMapping("/external/v1/overview")
@Tag(name = "Übersicht", description = "Die Kennzahlen der Startseite des Dashboards, ein Eintrag pro Tag.")
class ExternalOverviewController(
    private val service: ExternalReadService,
) {
    @GetMapping("/snapshots")
    @ExternalEndpoint(ExternalScope.OVERVIEW_SNAPSHOTS)
    fun snapshots(
        @Parameter(description = "Erster Tag, z. B. 2026-01-01; ohne Angabe 30 Tage vor `to`")
        @RequestParam(required = false)
        @Size(max = DATE_LENGTH)
        from: String?,
        @Parameter(description = "Letzter Tag, z. B. 2026-01-31; ohne Angabe heute. Höchstens 366 Tage ab `from`.")
        @RequestParam(required = false)
        @Size(max = DATE_LENGTH)
        to: String?,
    ): SnapshotsResponse = service.snapshots(from, to)
}

@RestController
@RequestMapping("/external/v1/dsks")
@Tag(name = "DSKs", description = "Die DSKs und die Militäreinheiten, die ihnen zugeordnet sind.")
class ExternalDskController(
    private val service: ExternalReadService,
) {
    @GetMapping
    @ExternalEndpoint(ExternalScope.DSKS_LIST)
    fun dsks(): List<DskResponse> = service.dsks()
}

@RestController
@RequestMapping("/external/v1/votes")
@Tag(
    name = "Abstimmungen",
    description = "Abstimmungen im Discord. Ergebnisse gibt es erst, wenn eine Abstimmung ausgewertet ist, nie einzelne Stimmen.",
)
class ExternalVoteController(
    private val service: ExternalReadService,
) {
    @GetMapping
    @ExternalEndpoint(ExternalScope.VOTES_LIST)
    fun votes(
        @Parameter(description = "Seite, ab 0") @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) page: Int,
        @Parameter(description = "Einträge pro Seite, 1 bis 100")
        @RequestParam(defaultValue = "${ExternalReadService.DEFAULT_PAGE_SIZE}")
        @Min(1)
        @Max(ExternalReadService.MAX_PAGE_SIZE)
        size: Int,
    ): PageResponse<VoteResponse> = service.votes(page, size)

    @GetMapping("/{id}")
    @ExternalEndpoint(ExternalScope.VOTES_GET)
    fun vote(
        @PathVariable id: Long,
    ): VoteResponse = service.vote(id)
}

/** Far beyond the data BITS has; keeps the offset of the query small. */
private const val MAX_PAGE = 100_000L

private const val DATE_LENGTH = 10

@Serializable
data class PageResponse<T>(
    val items: List<T>,
    /** From 0. */
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

@Serializable
data class AccountResponse(
    val discordUserId: String,
    val wareraUserId: String,
    /** At the time of linking; WarEra users can rename themselves. */
    val wareraUsername: String,
    /** ISO 8601. */
    val verifiedAt: String,
)

@Serializable
data class SnapshotsResponse(
    val from: String,
    val to: String,
    /** In order; days without figures are left out. */
    val days: List<SnapshotResponse>,
)

/** `null` is a figure that could not be read that day. */
@Serializable
data class SnapshotResponse(
    /** ISO 8601, the day in Europe/Berlin. */
    val day: String,
    /** Members of the Discord. */
    val members: Int?,
    /** Of them, those with a linked WarEra account. */
    val verifiedMembers: Int?,
    /** The WarEra country the citizens were counted for. */
    val countryId: String?,
    val citizens: Int?,
    val activeCitizens: Int?,
)

@Serializable
data class DskResponse(
    val id: Long,
    val name: String,
    /** The Discord role the members of its units get; `null` without one. */
    val roleId: String?,
    val militaryUnits: List<DskUnitResponse>,
)

@Serializable
data class DskUnitResponse(
    /** WarEra's ID of the military unit. */
    val id: String,
    /** As WarEra named it when it was assigned. */
    val name: String,
    /** ISO 8601. */
    val assignedAt: String,
)

@Serializable
data class VoteResponse(
    val id: Long,
    val question: String,
    /** The role whose members may vote, as named when the vote started. */
    val roleName: String?,
    val state: VoteStateResponse,
    /** ISO 8601, like the other times. */
    val startedAt: String,
    /** `null` for a vote that runs until somebody ends it. */
    val endsAt: String?,
    val closedAt: String?,
    /** At which the vote ends early, `null` if it does not. */
    val threshold: ThresholdResponse?,
    val endedByThreshold: Boolean,
    /** Only for [VoteStateResponse.EVALUATED]. */
    val result: VoteResultResponse?,
)

@Serializable
enum class VoteStateResponse {
    OPEN,

    /** Ended; the result is being counted. */
    COUNTING,
    EVALUATED,
    CANCELLED,
}

/** The vote ends early once more than [percent] of the eligible members chose [choice]. */
@Serializable
data class ThresholdResponse(
    val percent: Int,
    val choice: ThresholdChoice,
)

@Serializable
enum class ThresholdChoice {
    YES,
    NO,
}

@Serializable
data class VoteResultResponse(
    val yes: Int,
    val no: Int,
    val abstain: Int,
    val voted: Int,
    /** Members of the role with a linked account when the vote ended. */
    val eligible: Int,
    /** By size, those in no party last. */
    val parties: List<PartyResultResponse>,
)

@Serializable
data class PartyResultResponse(
    /** WarEra's ID of the party; `null` for those in no party and for some older votes. */
    val partyId: String?,
    /** `null` for those in no party. */
    val partyName: String?,
    val yes: Int,
    val no: Int,
    val abstain: Int,
    val voted: Int,
    val eligible: Int,
)
