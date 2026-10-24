package eu.luftiger.bits.backend.shared

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Checks what the bot sends to the endpoints under `/api`. The bot is trusted, but a bug in it or a leaked service token should end in
 * a 400 right away, not in a database error (500) or in odd input passed on to WarEra. The answer names no details.
 */
object BotInput {
    /** Snowflakes are up to 20 digits. */
    private val DISCORD_ID = Regex("[0-9]{1,20}")
    private val CODE = Regex("BITS-[A-Z0-9]{6}", RegexOption.IGNORE_CASE)

    /** Longer than any WarEra username; the bot's `/verify` option has the same limit. */
    const val MAX_WARERA_USERNAME_LENGTH = 32

    /** Discord allows 250 roles per guild and 100 characters per role name. */
    const val MAX_ROLES = 250
    const val MAX_ROLE_NAME_LENGTH = 100

    /** Role IDs are snowflakes as well; checked against the column only, so tests can use readable IDs. */
    private const val MAX_ROLE_ID_LENGTH = 32

    fun discordUserId(value: String) = check(DISCORD_ID.matches(value), "discordUserId")

    fun wareraUsername(value: String) = check(value.trim().length in 1..MAX_WARERA_USERNAME_LENGTH, "wareraUsername")

    fun code(value: String) = check(CODE.matches(value.trim()), "code")

    fun roleId(value: String) = check(value.isNotBlank() && value.length <= MAX_ROLE_ID_LENGTH, "role id")

    fun roleName(value: String) = check(value.length <= MAX_ROLE_NAME_LENGTH, "role name")

    fun roleCount(count: Int) = check(count <= MAX_ROLES, "number of roles")

    private fun check(
        valid: Boolean,
        field: String,
    ) {
        if (!valid) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $field")
    }
}
