package eu.luftiger.bits.backend.rolesync.api.dto

import eu.luftiger.bits.shared.Language
import eu.luftiger.bits.warera.WareraGovernment
import org.springframework.modulith.NamedInterface

/**
 * An office of a WarEra government, highest first, with its names. Ministers carry the [title] WarEra gives them in
 * [WareraGovernment.ministers]; several people may hold the same one.
 */
@NamedInterface("api")
enum class GovernmentOffice(
    val label: String,
    private val englishLabel: String,
    private val title: String? = null,
) {
    PRESIDENT("Präsident", "President"),
    VICE_PRESIDENT("Vizepräsident", "Vice President"),
    MINISTER_OF_ECONOMY("Wirtschaftsminister", "Minister of Economy", "minOfEconomy"),
    MINISTER_OF_DEFENSE("Verteidigungsminister", "Minister of Defense", "minOfDefense"),
    MINISTER_OF_FOREIGN_AFFAIRS("Außenminister", "Minister of Foreign Affairs", "minOfForeignAffairs"),
    MINISTER_OF_INTERIOR("Innenminister", "Minister of the Interior", "minOfInterior"),
    MINISTER_OF_COMMUNICATION("Kommunikationsminister", "Minister of Communication", "minOfCommunication"),
    CHIEF_OF_STAFF("Stabschef", "Chief of Staff", "chiefOfStaff"),
    ADVISOR("Berater", "Advisor", "advisor"),
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
            CONGRESS -> userId in government.congressMembers
            else -> government.ministers.any { it.user == userId && it.title == title }
        }

    companion object {
        private val titles = entries.mapNotNullTo(mutableSetOf()) { it.title }

        /** The titles of [government]'s ministers that no office stands for, e.g. a new ministry WarEra added. */
        fun unknownTitles(government: WareraGovernment): Set<String> = government.ministers.mapTo(sortedSetOf()) { it.title } - titles
    }
}
