package eu.luftiger.bits.backend.account.api

import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the account module: which members want their WarEra name as nickname. */
@NamedInterface("api")
interface NicknameOptIns {
    /** Discord user IDs of every member who opted in, linked or not. */
    fun discordUserIds(): Set<String>
}
