package eu.luftiger.bits.backend.rolesync.domain

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
 * Gives the Discord role [roleId] to every linked member whose WarEra account meets the condition. Which of the
 * condition fields are set depends on [type].
 */
@Entity
@Table(name = "role_rule")
class RoleRule(
    @Column(name = "role_id", nullable = false)
    val roleId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: ConditionType,
    /** For [ConditionType.CITIZENSHIP] and [ConditionType.GOVERNMENT_OFFICE]. */
    @Column(name = "country_id")
    val countryId: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "office")
    val office: GovernmentOffice? = null,
    @Column(name = "mu_id")
    val muId: String? = null,
    /** Lowest level or military rank for [ConditionType.LEVEL] and [ConditionType.MILITARY_RANK]. */
    @Column(name = "threshold")
    val threshold: Int? = null,
    /** Name of the country or military unit at the time the rule was created. */
    @Column(name = "target_name")
    val targetName: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /** German description for the dashboard and Discord's audit log. */
    val label: String
        get() =
            when (type) {
                ConditionType.CITIZENSHIP -> "Staatsbürgerschaft $targetName"
                ConditionType.GOVERNMENT_OFFICE -> "${office?.label} in $targetName"
                ConditionType.MILITARY_UNIT -> "Mitglied der MU $targetName"
                ConditionType.LEVEL -> "Level ab $threshold"
                ConditionType.MILITARY_RANK -> "Militärrang ab $threshold"
            }
}

enum class ConditionType {
    CITIZENSHIP,
    GOVERNMENT_OFFICE,
    MILITARY_UNIT,
    LEVEL,
    MILITARY_RANK,
}

enum class GovernmentOffice(
    val label: String,
) {
    PRESIDENT("Präsident"),
    VICE_PRESIDENT("Vizepräsident"),
    MINISTER_OF_ECONOMY("Wirtschaftsminister"),
    MINISTER_OF_DEFENSE("Verteidigungsminister"),
    MINISTER_OF_FOREIGN_AFFAIRS("Außenminister"),
    CONGRESS("Kongressmitglied"),
}
