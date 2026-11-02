package eu.luftiger.bits.backend.rolesync.api.dto

import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.warera.WareraGovernment
import org.springframework.modulith.NamedInterface

/** An office of a WarEra government, highest first, with its names. */
@NamedInterface("api")
enum class GovernmentOffice(
    val label: String,
    private val englishLabel: String,
) {
    PRESIDENT("Präsident", "President"),
    VICE_PRESIDENT("Vizepräsident", "Vice President"),
    MINISTER_OF_ECONOMY("Wirtschaftsminister", "Minister of Economy"),
    MINISTER_OF_DEFENSE("Verteidigungsminister", "Minister of Defense"),
    MINISTER_OF_FOREIGN_AFFAIRS("Außenminister", "Minister of Foreign Affairs"),
    CONGRESS("Kongressmitglied", "Member of Congress"),
    ;

    fun label(language: Language) = language.text(label, englishLabel)

    fun isHeldBy(
        government: WareraGovernment,
        userId: String,
    ): Boolean =
        when (this) {
            PRESIDENT -> government.president == userId
            VICE_PRESIDENT -> government.vicePresident == userId
            MINISTER_OF_ECONOMY -> government.minOfEconomy == userId
            MINISTER_OF_DEFENSE -> government.minOfDefense == userId
            MINISTER_OF_FOREIGN_AFFAIRS -> government.minOfForeignAffairs == userId
            CONGRESS -> userId in government.congressMembers
        }
}
