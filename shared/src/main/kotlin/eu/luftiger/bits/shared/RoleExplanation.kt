package eu.luftiger.bits.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoleExplanationRequest(
    val discordUserId: String,
)

/** Answer to `/rollen`. Each subtype is identified by its `type` field. */
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
    /** The rules of the role, any one of which is enough. */
    val rules: List<RuleExplanation>,
)

@Serializable
enum class ManagedRoleOutcome {
    MET,
    NOT_MET,

    /** No rule is met and the WarEra data for at least one is missing; the member keeps the role as it is. */
    UNDECIDED,
}

@Serializable
data class RuleExplanation(
    /** German description of the rule, as in the dashboard; its conditions joined with "und", all of which must be met. */
    val label: String,
    /** `null` if the WarEra data to decide is missing. */
    val met: Boolean?,
    /**
     * German description of what is missing, e.g. "Level 8 von 10": the conditions that fail joined with "und", or if
     * none fails, those that cannot be checked; `null` if the rule is met.
     */
    val detail: String?,
)
