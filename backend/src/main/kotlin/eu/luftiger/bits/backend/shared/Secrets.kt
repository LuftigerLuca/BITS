package eu.luftiger.bits.backend.shared

/**
 * Checks secrets from the environment at startup. A placeholder from `.env.example`, or one like it, would let
 * anybody who has read the repository forge sessions or call the bot's API.
 */
object Secrets {
    /** HS256 needs a key of at least 256 bits; the service token gets the same bar. */
    const val MIN_LENGTH = 32

    private val PLACEHOLDERS = listOf("change-me", "changeme", "secret", "password", "example")

    fun requireStrong(
        value: String,
        variable: String,
    ) {
        require(value.toByteArray().size >= MIN_LENGTH) {
            "$variable must be at least $MIN_LENGTH bytes long, e.g. from `openssl rand -hex 32`"
        }
        require(PLACEHOLDERS.none { value.contains(it, ignoreCase = true) }) {
            "$variable still holds a placeholder; set a random value, e.g. from `openssl rand -hex 32`"
        }
    }
}
