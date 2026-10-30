package eu.luftiger.bits.backend.vote.internal

import eu.luftiger.bits.backend.audit.api.AuditActor
import eu.luftiger.bits.backend.vote.domain.Choice
import eu.luftiger.bits.backend.vote.domain.Vote

/** Who starts, ends or cancels a vote: a member in Discord or a user of the dashboard. */
data class VoteManager(
    /** `discord:<id>` or `admin:<username>`, as in the audit log. */
    val subject: String,
    val name: String,
    val avatarUrl: String?,
    val admin: Boolean,
    val roleIds: List<String>,
) {
    fun auditActor() = AuditActor(subject, name, avatarUrl)
}

sealed interface StartOutcome {
    data class Started(
        val vote: Vote,
    ) : StartOutcome

    data object Forbidden : StartOutcome

    data object ChannelNotPostable : StartOutcome
}

sealed interface CloseOutcome {
    data object Closed : CloseOutcome

    data object Forbidden : CloseOutcome

    data object NotOpen : CloseOutcome
}

sealed interface CastOutcome {
    data class Cast(
        val previous: Choice?,
    ) : CastOutcome

    data object NotLinked : CastOutcome

    data object Closed : CastOutcome
}

/** Ends a vote early once more than [percent] of the eligible members chose [choice]. */
data class Threshold(
    val percent: Int,
    val choice: Choice,
)

data class Participation(
    val voted: Int,
    val eligible: Int,
)
