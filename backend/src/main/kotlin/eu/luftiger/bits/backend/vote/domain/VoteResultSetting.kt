package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** How the result image looks, as far as an admin changed it; there is at most one row. */
@Entity
@Table(name = "vote_result_setting")
class VoteResultSetting(
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    @Id
    @Column(name = "id")
    val id: Short = SINGLE_ROW_ID

    /** JPEG, `null` for the eagle. */
    @Column(name = "background")
    var background: ByteArray? = null

    /** 0xRRGGBB, `null` for the palette's grey. */
    @Column(name = "no_party_color")
    var noPartyColor: Int? = null

    companion object {
        const val SINGLE_ROW_ID: Short = 1
    }
}
