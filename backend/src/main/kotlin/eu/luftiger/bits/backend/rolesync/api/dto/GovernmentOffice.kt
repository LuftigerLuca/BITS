package eu.luftiger.bits.backend.rolesync.api.dto

import eu.luftiger.bits.warera.WareraGovernment
import org.springframework.modulith.NamedInterface

/** An office of a WarEra government, highest first, with its German name. */
@NamedInterface("api")
enum class GovernmentOffice(
    val label: String,
) {
    PRESIDENT("Präsident"),
    VICE_PRESIDENT("Vizepräsident"),
    MINISTER_OF_ECONOMY("Wirtschaftsminister"),
    MINISTER_OF_DEFENSE("Verteidigungsminister"),
    MINISTER_OF_FOREIGN_AFFAIRS("Außenminister"),
    CONGRESS("Kongressmitglied"),
    ;

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
