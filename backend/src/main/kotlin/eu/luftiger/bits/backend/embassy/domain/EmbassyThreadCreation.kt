package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * An embassy thread an admin asked the bot to create, until the bot reports it; at most one per country. Kept with
 * [failure] if creating it failed, so the dashboard can say why.
 */
@Entity
@Table(name = "embassy_thread_creation")
class EmbassyThreadCreation(
    @Column(name = "country_id", nullable = false)
    val countryId: String,
    @Column(name = "country_name", nullable = false)
    val countryName: String,
    @Column(name = "thread_name", nullable = false)
    val threadName: String,
    @Column(name = "channel_id", nullable = false)
    val channelId: String,
    @Column(name = "requested_at", nullable = false)
    val requestedAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** Why creating the thread failed, in German; `null` while the bot has not answered. */
    @Column(name = "failure")
    var failure: String? = null

    val pending: Boolean
        get() = failure == null
}
