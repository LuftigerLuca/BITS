package eu.luftiger.bits.backend.account.api

import eu.luftiger.bits.backend.account.api.dto.DepartedMember
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the account module for members who are gone from the guild. */
@NamedInterface("api")
interface AccountRemoval {
    /**
     * Deletes the links and open codes of [members], everything BITS keeps about them, and records each deleted link in
     * the system log. Unlike `/unverify`, no role sync follows, since they are no longer on the server; after joining
     * again they verify anew. Returns how many links were deleted.
     */
    fun removeDeparted(members: Collection<DepartedMember>): Int
}
