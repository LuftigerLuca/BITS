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

    /** Discord names are at most 32 characters; nicknames as well. Checked against the column, which leaves room. */
    private const val MAX_MEMBER_NAME_LENGTH = 100

    /** A sync of a large guild reports one change per member and role at most; anything beyond is a bug. */
    private const val MAX_REPORTED_CHANGES = 100_000

    /** Far above any guild BITS serves; a list beyond is a bug. */
    private const val MAX_MEMBERS = 1_000_000
    private const val MAX_REASON_LENGTH = 1_000
    private val STREAM_ID = Regex("[0-9]{1,20}-[0-9]{1,20}")
    private val EVENT_TYPE = Regex("[a-z_]{1,64}")

    /** Role IDs are snowflakes as well; checked against the column only, so tests can use readable IDs. */
    private const val MAX_ROLE_ID_LENGTH = 32

    fun discordUserId(value: String) = check(DISCORD_ID.matches(value), "discordUserId")

    fun wareraUsername(value: String) = check(value.trim().length in 1..MAX_WARERA_USERNAME_LENGTH, "wareraUsername")

    fun code(value: String) = check(CODE.matches(value.trim()), "code")

    fun roleId(value: String) = check(value.isNotBlank() && value.length <= MAX_ROLE_ID_LENGTH, "role id")

    fun roleName(value: String) = check(value.length <= MAX_ROLE_NAME_LENGTH, "role name")

    fun roleCount(count: Int) = check(count <= MAX_ROLES, "number of roles")

    fun memberName(value: String) = check(value.length <= MAX_MEMBER_NAME_LENGTH, "member name")

    fun memberCount(count: Int) = check(count <= MAX_MEMBERS, "number of members")

    fun reportedChangeCount(count: Int) = check(count <= MAX_REPORTED_CHANGES, "number of changes")

    fun reason(value: String) = check(value.length <= MAX_REASON_LENGTH, "reason")

    fun streamId(value: String) = check(STREAM_ID.matches(value), "stream id")

    fun eventType(value: String) = check(EVENT_TYPE.matches(value), "event type")

    /** Optional, so an older bot can leave it out. */
    fun discordName(value: String?) = value?.let(::memberName)

    private fun check(
        valid: Boolean,
        field: String,
    ) {
        if (!valid) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $field")
    }
}
