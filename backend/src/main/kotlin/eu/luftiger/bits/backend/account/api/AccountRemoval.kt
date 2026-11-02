package eu.luftiger.bits.backend.account.api

import eu.luftiger.bits.backend.account.api.dto.DepartedMember
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the account module for members who are gone from the guild. */
@NamedInterface("api")
interface AccountRemoval {
    /**
     * Deletes the links, open codes, nickname opt-ins and languages of [members], everything BITS keeps about them, and records each deleted link in
     * the system log. Unlike `/unverify`, no role sync follows, since they are no longer on the server; after joining
     * again they verify anew. Returns how many links were deleted.
     */
    fun removeDeparted(members: Collection<DepartedMember>): Int

    /**
     * Deletes the languages of everybody but [memberIds], the current members. A language works without a link, so somebody
     * who left while the bot was down is not among the departed links the member list finds.
     */
    fun removeLanguagesExcept(memberIds: Set<String>)
}
