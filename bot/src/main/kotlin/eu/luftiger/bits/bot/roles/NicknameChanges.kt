package eu.luftiger.bits.bot.roles

import eu.luftiger.bits.shared.RoleSyncEvent

data class NicknameChange(
    val discordUserId: String,
    /** `null` clears the nickname. */
    val nickname: String?,
)

/**
 * The change that brings the nickname of one member, who has [currentNickname] now, to the target of [event], or `null`
 * if it is there already or BITS leaves it alone. A reset only clears a nickname that is still the WarEra name.
 */
fun nicknameChange(
    discordUserId: String,
    currentNickname: String?,
    event: RoleSyncEvent,
): NicknameChange? {
    val nicknames = event.nicknames ?: return null
    nicknames[discordUserId]?.let { name ->
        val target = name.take(MAX_NICKNAME_LENGTH)
        return if (currentNickname == target) null else NicknameChange(discordUserId, target)
    }
    val former = event.nicknameResets[discordUserId]?.take(MAX_NICKNAME_LENGTH) ?: return null
    return if (currentNickname == former) NicknameChange(discordUserId, nickname = null) else null
}

/** Discord's limit; longer WarEra names are cut off. */
const val MAX_NICKNAME_LENGTH = 32

const val NICKNAME_REASON = "BITS: WarEra-Name"
