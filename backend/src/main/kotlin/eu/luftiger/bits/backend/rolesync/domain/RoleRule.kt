package eu.luftiger.bits.backend.rolesync.domain

import eu.luftiger.bits.backend.rolesync.api.dto.GovernmentOffice
import eu.luftiger.bits.warera.WareraMilitaryUnit
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.time.Instant

/**
 * Gives the Discord role [roleId] to every linked member whose WarEra account meets all of the [conditions]. Several
 * rules for the same role are combined with OR.
 */
@Entity
@Table(name = "role_rule")
class RoleRule(
    @Column(name = "role_id", nullable = false)
    val roleId: String,
    /** At least one, in the order the admin entered them. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_rule_condition", joinColumns = [JoinColumn(name = "rule_id")])
    @OrderColumn(name = "position")
    val conditions: List<RuleCondition>,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    init {
        require(conditions.isNotEmpty()) { "A rule needs at least one condition" }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    /**
     * The conditions regardless of their order, for the unique index that catches the same rule twice for a role.
     * V16 computes it the same way for the rules from before.
     */
    @Column(name = "conditions_key", nullable = false, length = 2000)
    val conditionsKey: String = conditions.map { it.key }.sorted().joinToString(";")

    /** German description for the dashboard and Discord's audit log, e.g. "Staatsbürgerschaft Germany und Level ab 10". */
    val label: String
        get() = conditions.joinToString(" und ") { it.label }
}

/** One condition of a [RoleRule]. Which of the fields are set depends on [type]. */
@Embeddable
class RuleCondition(
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: ConditionType,
    /**
     * For [ConditionType.CITIZENSHIP], [ConditionType.GOVERNMENT_OFFICE], [ConditionType.PARTY] and
     * [ConditionType.PARTY_LEADER].
     */
    @Column(name = "country_id")
    val countryId: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "office")
    val office: GovernmentOffice? = null,
    /** For [ConditionType.MILITARY_UNIT], and for [ConditionType.MU_LEADERSHIP] unless any military unit counts. */
    @Column(name = "mu_id")
    val muId: String? = null,
    /**
     * Lowest level, military rank or number of companies for [ConditionType.LEVEL], [ConditionType.MILITARY_RANK] and
     * [ConditionType.COMPANIES].
     */
    @Column(name = "threshold")
    val threshold: Int? = null,
    /**
     * Name of the country, military unit or party at the time the rule was created; for [ConditionType.MU_LEADERSHIP]
     * without a military unit `null`, for [ConditionType.PARTY_LEADER] without a party the country's.
     */
    @Column(name = "target_name")
    val targetName: String? = null,
    /**
     * For [ConditionType.PARTY], a party of the country [countryId]; for [ConditionType.PARTY_LEADER] too, unless any
     * party of the country counts.
     */
    @Column(name = "party_id")
    val partyId: String? = null,
    /** For [ConditionType.MU_LEADERSHIP], the lowest rank that counts. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mu_role")
    val muRole: MilitaryUnitRole? = null,
) {
    /** German description, e.g. "Level ab 10". */
    val label: String
        get() =
            when (type) {
                ConditionType.CITIZENSHIP -> "Staatsbürgerschaft $targetName"
                ConditionType.GOVERNMENT_OFFICE -> "${office?.label} in $targetName"
                ConditionType.MILITARY_UNIT -> "Mitglied der MU $targetName"
                ConditionType.LEVEL -> "Level ab $threshold"
                ConditionType.MILITARY_RANK -> "Militärrang ab $threshold"
                ConditionType.COMPANIES -> "Besitz von mindestens $threshold ${if (threshold == 1) "Firma" else "Firmen"}"
                ConditionType.PARTY -> "Mitglied der Partei $targetName"
                ConditionType.MU_LEADERSHIP -> "${muRole?.label} ${if (muId == null) "einer MU" else "der MU $targetName"}"
                ConditionType.PARTY_LEADER -> if (partyId == null) "Parteivorsitz in $targetName" else "Vorsitz der Partei $targetName"
            }

    /**
     * What makes two conditions the same; the name of the target is left out, since it may change in WarEra. The party
     * and the rank of the military unit come last and only if set, so the keys of the conditions from before them stay
     * as they are.
     */
    val key: String
        get() =
            listOfNotNull(
                type.name,
                countryId.orEmpty(),
                office?.name.orEmpty(),
                muId.orEmpty(),
                threshold?.toString().orEmpty(),
                partyId,
                muRole?.name,
            ).joinToString("|")
}

enum class ConditionType {
    CITIZENSHIP,
    GOVERNMENT_OFFICE,
    MILITARY_UNIT,
    LEVEL,
    MILITARY_RANK,

    /** Owns at least [RuleCondition.threshold] companies. */
    COMPANIES,

    PARTY,

    /** Holds at least the rank [RuleCondition.muRole] in the military unit [RuleCondition.muId], or in their own. */
    MU_LEADERSHIP,

    /** Leads the party [RuleCondition.partyId], or any party of the country [RuleCondition.countryId]. */
    PARTY_LEADER,
}

/** A rank in a military unit, lowest first, with its German name; each includes the ranks above it. */
enum class MilitaryUnitRole(
    val label: String,
) {
    COMMANDER("Kommandeur"),
    MANAGER("Manager"),
    OWNER("Besitzer"),
    ;

    /** Whether [userId] holds this rank or a higher one in [mu]. */
    fun isHeldBy(
        mu: WareraMilitaryUnit,
        userId: String,
    ): Boolean =
        when (this) {
            OWNER -> mu.owner == userId
            MANAGER -> OWNER.isHeldBy(mu, userId) || userId in mu.roles.managers
            COMMANDER -> MANAGER.isHeldBy(mu, userId) || userId in mu.roles.commanders
        }
}
