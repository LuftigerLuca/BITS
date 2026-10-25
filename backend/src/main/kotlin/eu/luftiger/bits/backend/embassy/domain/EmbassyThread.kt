package eu.luftiger.bits.backend.embassy.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** The embassy thread of a foreign country; the names are those at the time it was assigned. */
@Entity
@Table(name = "embassy_thread")
class EmbassyThread(
    @Id
    @Column(name = "thread_id")
    val threadId: String,
    @Column(name = "thread_name", nullable = false)
    val threadName: String,
    @Column(name = "parent_channel_id", nullable = false)
    val parentChannelId: String,
    @Column(name = "country_id", nullable = false)
    val countryId: String,
    @Column(name = "country_name", nullable = false)
    val countryName: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)
