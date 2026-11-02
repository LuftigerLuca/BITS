package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoleExplanationRequest(
    val discordUserId: String,
    /** The language of the labels and details of the answer. */
    val language: Language = Language.GERMAN,
)

/** Answer to `/roles`. Each subtype is identified by its `type` field. */
@Serializable
sealed interface RoleExplanationResponse {
    @Serializable
    @SerialName("not_linked")
    data object NotLinked : RoleExplanationResponse

    /** Judged with fresh WarEra data of the user, the same way the role sync does. */
    @Serializable
    @SerialName("explained")
    data class Explained(
        val wareraUsername: String,
        /** Every role BITS manages, highest in the guild first; empty if there are no rules. */
        val roles: List<ManagedRoleExplanation>,
    ) : RoleExplanationResponse
}

@Serializable
data class ManagedRoleExplanation(
    val roleId: String,
    val outcome: ManagedRoleOutcome,
    /**
     * The blocks of the rule of the role, any one of which is enough. Named `rules` from when a role had several rules
     * instead, which bot and backend still expect.
     */
    val rules: List<RuleExplanation>,
)

@Serializable
enum class ManagedRoleOutcome {
    MET,
    NOT_MET,

    /** No block is met and the WarEra data for at least one is missing; the member keeps the role as it is. */
    UNDECIDED,
}

/** One block of the rule of a role. */
@Serializable
data class RuleExplanation(
    /** Description of the block in the requested language; its conditions joined with "und", all of which must be met. */
    val label: String,
    /** `null` if the WarEra data to decide is missing. */
    val met: Boolean?,
    /**
     * Description of what is missing in the requested language, e.g. "Level 8 von 10": the conditions that fail joined with "und", or if
     * none fails, those that cannot be checked; `null` if the block is met.
     */
    val detail: String?,
)
