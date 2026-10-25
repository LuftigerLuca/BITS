package eu.luftiger.bits.backend.embassy.domain

import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

/** An office whose holders belong in the embassy thread of their country. */
@Entity
@Table(name = "embassy_office")
class EmbassyOffice(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "office")
    val office: GovernmentOffice,
)
