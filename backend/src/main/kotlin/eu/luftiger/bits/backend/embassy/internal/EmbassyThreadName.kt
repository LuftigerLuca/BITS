package eu.luftiger.bits.backend.embassy.internal

/** The name template of embassy threads, e.g. "🏛️ Botschaft {land}". */
internal object EmbassyThreadName {
    const val PLACEHOLDER = "{land}"
    const val DEFAULT_TEMPLATE = "Botschaft $PLACEHOLDER"

    /** Discord's limit for thread names, and the template's. */
    const val MAX_LENGTH = 100

    /** Whether [template], trimmed, has 1 to [MAX_LENGTH] characters and [PLACEHOLDER] exactly once, case-sensitive. */
    fun isValid(template: String): Boolean {
        val trimmed = template.trim()
        return trimmed.length in 1..MAX_LENGTH && trimmed.split(PLACEHOLDER).size == 2
    }

    /** [template] with the country name as WarEra has it, cut to [MAX_LENGTH]. */
    fun of(
        template: String,
        countryName: String,
    ): String = template.replace(PLACEHOLDER, countryName).take(MAX_LENGTH)
}
