package eu.luftiger.bits.backend.shared

/**
 * Upper limits for what the dashboard sends under `/web`. Request bodies are checked with Bean Validation
 * (`@Valid` and `@field:Size`), parameters with `@Size`, so overlong input ends in a 400 before it reaches WarEra or
 * the database. Limits that belong to a feature (e.g. the length of a question) stay with its service; the bot's
 * input under `/api` is checked with [BotInput].
 */
object WebInput {
    /** Discord IDs are snowflakes of up to 20 digits; like [BotInput], the limit leaves room for readable test IDs. */
    const val MAX_ID_LENGTH = 32

    /** WarEra IDs are 24 hex characters. */
    const val MAX_WARERA_ID_LENGTH = 64

    /** Search terms and filters by name; longer than any name in Discord or WarEra. */
    const val MAX_QUERY_LENGTH = 100

    /** Discord allows 250 roles per guild. */
    const val MAX_ROLES = BotInput.MAX_ROLES

    /** More than WarEra has offices. */
    const val MAX_OFFICES = 20

    /** More than a country has parties. */
    const val MAX_PARTIES = 100

    /** `#rrggbb` */
    const val MAX_COLOR_LENGTH = 7

    const val MAX_USERNAME_LENGTH = 100

    const val MAX_PASSWORD_LENGTH = 128
}
