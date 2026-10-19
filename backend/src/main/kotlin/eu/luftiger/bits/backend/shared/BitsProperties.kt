package eu.luftiger.bits.backend.shared

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bits")
data class BitsProperties(
    /** Token the bot sends as `Authorization: Bearer <token>` to call the API. */
    val serviceToken: String,
    val warera: Warera,
) {
    init {
        require(serviceToken.isNotBlank()) { "bits.service-token must not be blank" }
    }

    data class Warera(
        val baseUrl: String,
    )
}
