package eu.luftiger.bits.backend.rolesync.api.dto

import eu.luftiger.bits.shared.ManagedRoleExplanation
import org.springframework.modulith.NamedInterface

/** Judged with fresh WarEra data of the member, the same way the role sync does. */
@NamedInterface("api")
data class MemberRoleExplanation(
    val wareraUsername: String,
    /** Every role BITS manages, highest in the guild first; empty if there are no rules. */
    val roles: List<ExplainedRole>,
)

@NamedInterface("api")
data class ExplainedRole(
    /** As the bot reported the role last. */
    val roleName: String,
    val roleColor: Int?,
    val explanation: ManagedRoleExplanation,
)
