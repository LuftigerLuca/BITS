package eu.luftiger.bits.backend.vote.api

import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the vote module: who may manage votes, which other features with the same audience use. */
@NamedInterface("api")
interface VoteManagers {
    /** Whether an admin, or a member of [roleIds], may manage votes; the roles come from the bot or the session. */
    fun mayManage(
        admin: Boolean,
        roleIds: Collection<String>,
    ): Boolean
}
