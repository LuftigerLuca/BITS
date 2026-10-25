package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A yes/no/abstain vote among the members of the role [roleId]. It goes from [VoteStatus.OPEN] to
 * [VoteStatus.COUNTING] and [VoteStatus.EVALUATED], or to [VoteStatus.CANCELLED]. Names are snapshots, see the
 * migration of `vote`.
 */
@Entity
@Table(name = "vote")
class Vote(
    @Column(name = "question", nullable = false)
    val question: String,
    @Column(name = "role_id", nullable = false)
    val roleId: String,
    @Column(name = "role_name")
    val roleName: String?,
    @Column(name = "channel_id", nullable = false)
    val channelId: String,
    @Column(name = "channel_name")
    val channelName: String?,
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,
    @Column(name = "started_by_name", nullable = false)
    val startedByName: String,
    /** `null` for a vote that runs until somebody ends it. */
    @Column(name = "ends_at")
    val endsAt: Instant?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    var status: VoteStatus = VoteStatus.OPEN

    @Column(name = "message_id")
    var messageId: String? = null

    @Column(name = "closed_at")
    var closedAt: Instant? = null

    /** `null` if the vote ended on its own. */
    @Column(name = "closed_by_name")
    var closedByName: String? = null

    /** Members of the role with a linked account, as of the bot's last report. */
    @Column(name = "eligible_count")
    var eligibleCount: Int? = null

    @Column(name = "electorate_reported_at")
    var electorateReportedAt: Instant? = null

    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null

    @Column(name = "problem_recorded", nullable = false)
    var problemRecorded: Boolean = false

    @Column(name = "evaluated_at")
    var evaluatedAt: Instant? = null

    @Column(name = "result_posted_at")
    var resultPostedAt: Instant? = null
}

enum class VoteStatus {
    OPEN,

    /** Ended; waits for the role members from the bot and the parties from WarEra. */
    COUNTING,
    EVALUATED,
    CANCELLED,
}
