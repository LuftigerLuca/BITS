package eu.luftiger.bits.backend.vote.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** The colour an admin picked for a WarEra party in the result image. */
@Entity
@Table(name = "vote_party_color")
class PartyColor(
    @Id
    @Column(name = "party_id")
    val partyId: String,
    /** The WarEra country of the party when the colour was picked. */
    @Column(name = "country_id", nullable = false)
    val countryId: String,
    /** 0xRRGGBB. */
    @Column(name = "color", nullable = false)
    val color: Int,
)
