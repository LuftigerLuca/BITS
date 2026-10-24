package eu.luftiger.bits.backend.account.api

import eu.luftiger.bits.backend.account.api.dto.LinkedAccount
import eu.luftiger.bits.backend.account.api.dto.LinkedWareraAccount
import org.springframework.modulith.NamedInterface

/** Cross-module boundary of the account module: tells which WarEra account a Discord account is linked to. */
@NamedInterface("api")
interface AccountLinks {
    fun findByDiscordUserId(discordUserId: String): LinkedWareraAccount?

    fun findAll(): List<LinkedAccount>
}
